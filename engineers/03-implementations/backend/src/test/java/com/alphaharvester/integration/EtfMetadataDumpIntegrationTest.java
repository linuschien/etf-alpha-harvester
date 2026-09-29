package com.alphaharvester.integration;

import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.model.SyncScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
public class EtfMetadataDumpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(EtfMetadataDumpIntegrationTest.class);
    private static final DateTimeFormatter SQL_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern STAGE_0_ALLOWLIST_PATTERN = Pattern.compile("^00\\d{2,4}B?$");

    @Autowired
    private MarketDataSyncUseCase marketDataSyncUseCase;

    @Autowired
    private GlobalAssetMetadataRepository metadataRepository;

    @Test
    @DisplayName("Fetch official TWSE and TPEx ETF metadata via Service layer, persist to DB, and dump into Flyway V3 seed script")
    void shouldFetchAndDumpOfficialEtfMetadataToFlywayV3() throws IOException {
        log.info("Starting live fetch of TWSE and TPEx ETF master universe via MarketDataSyncUseCase...");

        // 1. Wipe metadata table to ensure clean state
        metadataRepository.deleteAll().block();

        // 2. Trigger official metadata synchronization via application service layer
        MarketDataSyncResponse response = marketDataSyncUseCase.syncMarketData(
                new MarketDataSyncRequest(SyncScope.METADATA, false)
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUCCESS");
        int syncedCount = response.syncedRecords().etfAssetsCount();
        log.info("MarketDataSyncUseCase synced {} ETF assets.", syncedCount);
        assertThat(syncedCount).isGreaterThanOrEqualTo(300);

        // 3. Query back all metadata from DB
        List<GlobalAssetMetadata> dbAssets = metadataRepository.findAll()
                .collectList()
                .block();

        assertThat(dbAssets).isNotNull().isNotEmpty();
        dbAssets.sort(Comparator.comparing(GlobalAssetMetadata::getTicker));
        log.info("Read back {} ETFs from database for Flyway V3 export.", dbAssets.size());

        // Validate benchmark and allowlist constraints
        assertThat(dbAssets.stream().anyMatch(a -> "0050".equals(a.getTicker()))).isTrue();
        assertThat(dbAssets.stream().anyMatch(a -> a.getTicker().endsWith("B"))).isTrue();
        assertThat(dbAssets).allMatch(a -> STAGE_0_ALLOWLIST_PATTERN.matcher(a.getTicker()).matches());

        // 4. Generate Flyway V3 SQL seed script
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

        // Append watermark update for TWSE_ETF_METADATA
        sql.append("\n-- Update watermark for TWSE_ETF_METADATA\n");
        sql.append(String.format("""
                UPDATE data_feed_sync_watermark
                SET latest_record_date = TIMESTAMP '%s 00:00:00',
                    records_synced_count = %d,
                    status = 'SUCCESS',
                    updated_at = CURRENT_TIMESTAMP
                WHERE feed_name = 'TWSE_ETF_METADATA';
                """, java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE), dbAssets.size()));

        // 5. Write to src/main/resources/db/migration/V3__seed_etf_metadata.sql
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
