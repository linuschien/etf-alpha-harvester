package com.alphaharvester.integration;

import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.domain.entity.DcaPopularityRank;
import com.alphaharvester.domain.model.SyncScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class DcaPopularityRankDumpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DcaPopularityRankDumpIntegrationTest.class);

    @Autowired
    private MarketDataSyncUseCase marketDataSyncUseCase;

    @Autowired
    private DcaPopularityRankRepository dcaRankRepository;

    @Autowired
    private GlobalAssetMetadataRepository metadataRepository;

    @Test
    @DisplayName("Fetch latest official TWSE regular quota (DCA) Top 20 ETF rankings via Service layer, persist to DB, and dump into Flyway V6 script")
    void shouldFetchAndDumpLatestTwseDcaRankingsToFlywayV6() throws IOException {
        log.info("Starting live TWSE regular quota (DCA) rankings synchronization via MarketDataSyncUseCase...");

        // 1. Verify prerequisite metadata (V3 seed data) is populated
        Long assetCount = metadataRepository.count().block();
        log.info("Current universe has {} ETFs in metadataRepository.", assetCount);
        assertThat(assetCount).isGreaterThanOrEqualTo(300);

        // 2. Clear existing DCA rankings in DB to ensure clean state
        dcaRankRepository.deleteAll().block();

        // 3. Trigger live DCA rankings sync via Service layer (August 2026 report)
        MarketDataSyncRequest request = new MarketDataSyncRequest(SyncScope.DCA_RANKS, false);
        MarketDataSyncResponse response = marketDataSyncUseCase.syncMarketData(request).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUCCESS");
        int totalDcaSynced = response.syncedRecords().dcaPopularityRanksCount();
        log.info("MarketDataSyncUseCase completed DCA sync: total {} rankings recorded in response.", totalDcaSynced);
        assertThat(totalDcaSynced).isEqualTo(20);

        // 4. Query back all DCA rankings from DB
        List<DcaPopularityRank> allRanks = dcaRankRepository.findAll().collectList().block();
        assertThat(allRanks).isNotNull().hasSize(20);
        log.info("Successfully retrieved {} DCA popularity rank records from database.", allRanks.size());

        // Sort deterministically by rankPosition ASC (1 to 20)
        allRanks.sort(Comparator.comparing(DcaPopularityRank::getRankPosition));

        // Validate top ranks
        DcaPopularityRank rank1 = allRanks.get(0);
        assertThat(rank1.getRankPosition()).isEqualTo(1);
        assertThat(rank1.getTicker()).isEqualTo("0050");
        assertThat(rank1.getRankingYear()).isEqualTo(2026);
        assertThat(rank1.getRankingMonth()).isEqualTo(8);
        assertThat(rank1.getRegularInvestorCount()).isGreaterThan(1000000);

        // 5. Resolve target migration directory
        Path targetDir = Paths.get("src/main/resources/db/migration");
        if (!Files.exists(targetDir)) {
            targetDir = Paths.get("engineers/03-implementations/backend/src/main/resources/db/migration");
        }

        String fileName = "V6__seed_twse_dca_rankings.sql";
        Path filePath = targetDir.resolve(fileName);

        StringBuilder sql = new StringBuilder();
        sql.append("-- ").append(fileName).append("\n");
        sql.append("-- Seed official TWSE regular quota (DCA) Top 20 ETF investor popularity rankings (Auto-generated from TWSE OpenAPI)\n");
        sql.append("-- Ranking Period: 2026-08 (Total records: ").append(allRanks.size()).append(")\n\n");

        sql.append("ALTER TABLE dca_popularity_rank ALTER COLUMN asset_id DROP NOT NULL;\n\n");
        sql.append("INSERT INTO dca_popularity_rank (id, asset_id, ticker, ranking_year, ranking_month, rank_position, regular_investor_count)\nVALUES\n");

        for (int i = 0; i < allRanks.size(); i++) {
            DcaPopularityRank r = allRanks.get(i);
            String isLast = (i == allRanks.size() - 1) ? ";" : ",";
            String assetIdStr = r.getAssetId() != null ? String.format("'%s'", r.getAssetId()) : "NULL";

            sql.append(String.format("    ('%s', %s, '%s', %d, %d, %d, %d)%s\n",
                    r.getId(),
                    assetIdStr,
                    r.getTicker(),
                    r.getRankingYear(),
                    r.getRankingMonth(),
                    r.getRankPosition(),
                    r.getRegularInvestorCount(),
                    isLast
            ));
        }

        sql.append("\n-- Update watermark for TWSE_DCA_RANKINGS\n");
        sql.append(String.format(
                "UPDATE data_feed_sync_watermark\n" +
                "SET latest_record_date = TIMESTAMP '%04d-%02d-31 00:00:00',\n" +
                "    records_synced_count = %d,\n" +
                "    status = 'SUCCESS',\n" +
                "    updated_at = CURRENT_TIMESTAMP\n" +
                "WHERE feed_name = 'TWSE_DCA_RANKINGS';\n",
                rank1.getRankingYear(),
                rank1.getRankingMonth(),
                allRanks.size()
        ));

        Files.writeString(filePath, sql.toString(), StandardCharsets.UTF_8);
        log.info("Successfully generated Flyway V6 migration file: {} ({} records)", filePath.toAbsolutePath(), allRanks.size());
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.size(filePath)).isGreaterThan(500L);
    }
}
