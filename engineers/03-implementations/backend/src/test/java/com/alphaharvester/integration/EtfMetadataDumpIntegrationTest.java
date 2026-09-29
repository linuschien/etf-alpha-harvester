package com.alphaharvester.integration;

import com.alphaharvester.adapter.out.external.TpexMarketDataClient;
import com.alphaharvester.adapter.out.external.TwseMarketDataClient;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
public class EtfMetadataDumpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(EtfMetadataDumpIntegrationTest.class);
    private static final DateTimeFormatter SQL_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TwseMarketDataClient twseClient;

    @Autowired
    private TpexMarketDataClient tpexClient;

    @Autowired
    private GlobalAssetMetadataRepository metadataRepository;

    @Test
    @DisplayName("Fetch official TWSE and TPEx ETF metadata, save to DB, and dump into Flyway V3 seed script")
    void shouldFetchAndDumpOfficialEtfMetadataToFlywayV3() throws IOException {
        log.info("Starting live fetch of TWSE and TPEx ETF master universe...");

        // 1. Fetch live metadata concurrently from TWSE & TPEx
        List<GlobalAssetMetadata> liveAssets = Flux.concat(
                twseClient.fetchEtfMasterUniverse(),
                tpexClient.fetchTpexEtfMasterUniverse()
        ).collectList().block();

        assertThat(liveAssets).isNotNull().isNotEmpty();
        log.info("Successfully fetched {} candidate ETFs from TWSE and TPEx OpenData.", liveAssets.size());

        // 2. Assign deterministic UUID and deduplicate by ticker (in case of overlap)
        Map<String, GlobalAssetMetadata> uniqueAssetsMap = new TreeMap<>();
        for (GlobalAssetMetadata asset : liveAssets) {
            if (asset.getTicker() == null || asset.getTicker().isBlank()) continue;
            String ticker = asset.getTicker().trim();
            UUID deterministicId = UUID.nameUUIDFromBytes(("ALPHA-ETF:" + ticker).getBytes(StandardCharsets.UTF_8));
            asset.setId(deterministicId);
            asset.setVersion(null);
            uniqueAssetsMap.put(ticker, asset);
        }

        log.info("Deduplicated to {} unique prototype ETFs.", uniqueAssetsMap.size());
        assertThat(uniqueAssetsMap).containsKey("0050"); // TWSE benchmark
        assertThat(uniqueAssetsMap.keySet().stream().anyMatch(t -> t.endsWith("B"))).isTrue(); // TPEx bond ETFs
        Pattern allowlistPattern = Pattern.compile("^00\\d{2,4}B?$");
        assertThat(uniqueAssetsMap.keySet()).allMatch(t -> allowlistPattern.matcher(t).matches());

        // 3. Persist into DB using R2DBC repository
        metadataRepository.deleteAll().block();
        for (GlobalAssetMetadata asset : uniqueAssetsMap.values()) {
            metadataRepository.findByTicker(asset.getTicker())
                    .flatMap(existing -> {
                        existing.setName(asset.getName());
                        if (asset.getUnderlyingIndex() != null) {
                            existing.setUnderlyingIndex(asset.getUnderlyingIndex());
                        }
                        if (asset.getListingDate() != null) {
                            existing.setListingDate(asset.getListingDate());
                        }
                        return metadataRepository.save(existing);
                    })
                    .switchIfEmpty(metadataRepository.save(asset))
                    .block();
        }

        // 4. Query back all metadata from DB
        List<GlobalAssetMetadata> dbAssets = metadataRepository.findAll()
                .collectList()
                .block();

        assertThat(dbAssets).isNotNull().isNotEmpty();
        dbAssets.sort(Comparator.comparing(GlobalAssetMetadata::getTicker));
        log.info("Read back {} ETFs from database for Flyway V3 export.", dbAssets.size());

        // 5. Generate Flyway V3 SQL seed script
        StringBuilder sql = new StringBuilder();
        sql.append("-- V3__seed_etf_metadata.sql\n");
        sql.append("-- Seed official TWSE & TPEx ETF master universe (Auto-generated from live OpenData)\n");
        sql.append("-- Total qualified prototype ETFs: ").append(dbAssets.size()).append("\n\n");
        sql.append("INSERT INTO global_asset_metadata (id, ticker, name, listing_date, underlying_index, version, created_at, updated_at)\n");
        sql.append("VALUES\n");

        for (int i = 0; i < dbAssets.size(); i++) {
            GlobalAssetMetadata a = dbAssets.get(i);
            String idStr = a.getId().toString();
            String ticker = a.getTicker();
            String name = a.getName().replace("'", "''");
            String listingDate = a.getListingDate() != null
                    ? "'" + a.getListingDate().format(SQL_TIMESTAMP_FMT) + "'"
                    : "CURRENT_TIMESTAMP";
            String index = (a.getUnderlyingIndex() != null && !a.getUnderlyingIndex().isBlank())
                    ? "'" + a.getUnderlyingIndex().replace("'", "''") + "'"
                    : "NULL";

            sql.append(String.format("    ('%s', '%s', '%s', TIMESTAMP %s, %s, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    idStr, ticker, name, listingDate, index));

            if (i < dbAssets.size() - 1) {
                sql.append(",\n");
            } else {
                sql.append(";\n");
            }
        }

        // 6. Write to src/main/resources/db/migration/V3__seed_etf_metadata.sql
        Path targetDir = Paths.get("src/main/resources/db/migration");
        if (!Files.exists(targetDir)) {
            targetDir = Paths.get("engineers/03-implementations/backend/src/main/resources/db/migration");
        }
        Path migrationPath = targetDir.resolve("V3__seed_etf_metadata.sql");

        Files.writeString(migrationPath, sql.toString(), StandardCharsets.UTF_8);
        log.info("Successfully generated Flyway V3 migration file at: {} ({} bytes)",
                migrationPath.toAbsolutePath(), Files.size(migrationPath));

        assertThat(Files.exists(migrationPath)).isTrue();
        assertThat(Files.size(migrationPath)).isGreaterThan(1000L);
    }
}
