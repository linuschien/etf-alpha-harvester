package com.alphaharvester.integration;

import com.alphaharvester.adapter.out.persistence.MacroYieldSnapshotRepository;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class MacroYieldDumpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MacroYieldDumpIntegrationTest.class);
    private static final DateTimeFormatter SQL_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MarketDataSyncUseCase marketDataSyncUseCase;

    @Autowired
    private MacroYieldSnapshotRepository macroYieldRepository;

    @Test
    @DisplayName("Fetch 2-year historical macroeconomic yields from FRED via Service layer, persist to DB, and dump into Flyway V5 script")
    void shouldFetchAndDumpTwoYearMacroYieldsToFlywayV5() throws IOException {
        log.info("Starting live 2-year macroeconomic yields synchronization via MarketDataSyncUseCase...");

        // 1. Clear existing snapshots in DB to ensure clean state
        macroYieldRepository.deleteAll().block();

        // 2. Trigger 2-year (760 days, from 2024-09-01) historical macro yields sync via Service layer
        MarketDataSyncRequest request = new MarketDataSyncRequest(SyncScope.MACRO_YIELDS, false, 760);
        MarketDataSyncResponse response = marketDataSyncUseCase.syncMarketData(request).block();

        assertThat(response).isNotNull();
        int totalYieldsSynced = response.syncedRecords().macroYieldSnapshotsCount();
        log.info("MarketDataSyncUseCase completed macro yields sync: total {} snapshots recorded in response.", totalYieldsSynced);
        assertThat(totalYieldsSynced).isGreaterThanOrEqualTo(480);

        // 3. Query back all macro yield snapshots from DB
        List<MacroYieldSnapshot> allSnapshots = macroYieldRepository.findAll().collectList().block();
        assertThat(allSnapshots).isNotNull().isNotEmpty();
        log.info("Successfully retrieved {} macro yield snapshots from database.", allSnapshots.size());
        assertThat(allSnapshots.size()).isGreaterThanOrEqualTo(480);

        // Sort deterministically by recordDate ASC
        allSnapshots.sort(Comparator.comparing(MacroYieldSnapshot::getRecordDate));

        // 4. Resolve target migration directory
        Path targetDir = Paths.get("src/main/resources/db/migration");
        if (!Files.exists(targetDir)) {
            targetDir = Paths.get("engineers/03-implementations/backend/src/main/resources/db/migration");
        }

        String fileName = "V5__seed_macro_yield_snapshots.sql";
        Path filePath = targetDir.resolve(fileName);

        StringBuilder sql = new StringBuilder();
        sql.append("-- ").append(fileName).append("\n");
        sql.append("-- Seed official 2-year historical macroeconomic yields (Auto-generated from St. Louis Fed FRED)\n");
        sql.append("-- Total records: ").append(allSnapshots.size()).append("\n\n");

        sql.append("INSERT INTO macro_yield_snapshot (id, record_date, us_corporate_bond_effective_yield, us_10_year_treasury_yield, us_20_year_treasury_yield, yield_spread_10y_minus_2y)\nVALUES\n");

        for (int i = 0; i < allSnapshots.size(); i++) {
            MacroYieldSnapshot s = allSnapshots.get(i);
            String isLast = (i == allSnapshots.size() - 1) ? ";" : ",";
            sql.append(String.format("    ('%s', TIMESTAMP '%s', %.4f, %.4f, %.4f, %.4f)%s\n",
                    s.getId(),
                    s.getRecordDate().format(SQL_TIMESTAMP_FMT),
                    s.getUsCorporateBondEffectiveYield(),
                    s.getUs10YearTreasuryYield(),
                    s.getUs20YearTreasuryYield(),
                    s.getYieldSpread10yMinus2y(),
                    isLast
            ));
        }

        sql.append("\n-- Update watermark for MACRO_YIELD_SNAPSHOT\n");
        MacroYieldSnapshot latest = allSnapshots.get(allSnapshots.size() - 1);
        sql.append(String.format(
                "UPDATE data_feed_sync_watermark\n" +
                "SET latest_record_date = TIMESTAMP '%s',\n" +
                "    records_synced_count = %d,\n" +
                "    status = 'SUCCESS',\n" +
                "    updated_at = CURRENT_TIMESTAMP\n" +
                "WHERE feed_name = 'MACRO_YIELD_SNAPSHOT';\n",
                latest.getRecordDate().format(SQL_TIMESTAMP_FMT),
                allSnapshots.size()
        ));

        Files.writeString(filePath, sql.toString(), StandardCharsets.UTF_8);
        log.info("Successfully generated Flyway V5 migration file: {} ({} records)", filePath.toAbsolutePath(), allSnapshots.size());
    }
}
