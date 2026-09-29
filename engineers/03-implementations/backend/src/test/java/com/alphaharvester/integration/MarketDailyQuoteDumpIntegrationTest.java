package com.alphaharvester.integration;

import com.alphaharvester.adapter.out.persistence.BenchmarkIndexRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.domain.entity.MarketDailyQuote;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Timeout(value = 15, unit = TimeUnit.MINUTES)
public class MarketDailyQuoteDumpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MarketDailyQuoteDumpIntegrationTest.class);
    private static final DateTimeFormatter SQL_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_KEY_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int BATCH_SIZE = 1000;

    @Autowired
    private MarketDataSyncUseCase marketDataSyncUseCase;

    @Autowired
    private MarketDailyQuoteRepository quoteRepository;

    @Autowired
    private GlobalAssetMetadataRepository metadataRepository;

    @Autowired
    private BenchmarkIndexRepository benchmarkRepository;

    @Test
    @DisplayName("Fetch 2-year historical daily quotes for ETFs and Benchmarks via Service layer, persist to DB, and dump into monthly Flyway V4 scripts")
    void shouldFetchAndDumpTwoYearMarketDailyQuotesToFlywayV4() throws IOException {
        log.info("Starting live 2-year market daily quotes synchronization via MarketDataSyncUseCase...");

        // 1. Verify prerequisite metadata (V3 seed data) and benchmarks (V2 seed data) are populated
        Long assetCount = metadataRepository.count().block();
        Long benchmarkCount = benchmarkRepository.count().block();
        log.info("Current universe has {} ETFs in metadataRepository and {} benchmark indices.", assetCount, benchmarkCount);
        assertThat(assetCount).isGreaterThanOrEqualTo(300);
        assertThat(benchmarkCount).isGreaterThanOrEqualTo(8);

        // 2. Clear existing quotes in DB to ensure clean state
        quoteRepository.deleteAll().block();

        // 3. Trigger 2-year (730 days) historical daily quotes sync via Service layer
        MarketDataSyncRequest request = new MarketDataSyncRequest(SyncScope.QUOTES, false, 730);
        MarketDataSyncResponse response = marketDataSyncUseCase.syncMarketData(request).block();

        assertThat(response).isNotNull();
        int totalQuotesSynced = response.syncedRecords().dailyQuotesCount();
        log.info("MarketDataSyncUseCase completed quote sync: total {} quotes recorded in response.", totalQuotesSynced);

        // 4. Query back all daily quotes from DB
        List<MarketDailyQuote> allQuotes = quoteRepository.findAll().collectList().block();
        assertThat(allQuotes).isNotNull().isNotEmpty();
        log.info("Successfully retrieved {} daily quote records from database.", allQuotes.size());
        assertThat(allQuotes.size()).isGreaterThanOrEqualTo(30000);

        // 5. Group quotes by Year-Month (e.g., "202410", "202411", ..., "202609")
        Map<String, List<MarketDailyQuote>> quotesByMonth = allQuotes.stream()
                .filter(q -> q.getTradeDate() != null && q.getClosePrice() != null)
                .collect(Collectors.groupingBy(
                        q -> q.getTradeDate().format(MONTH_KEY_FMT),
                        TreeMap::new,
                        Collectors.toList()
                ));

        log.info("Grouped quotes into {} distinct monthly buckets: {}", quotesByMonth.size(), quotesByMonth.keySet());
        assertThat(quotesByMonth).isNotEmpty();

        // 6. Resolve target migration directory
        Path targetDir = Paths.get("src/main/resources/db/migration");
        if (!Files.exists(targetDir)) {
            targetDir = Paths.get("engineers/03-implementations/backend/src/main/resources/db/migration");
        }

        // 7. Generate Flyway V4 migration file for each month
        for (Map.Entry<String, List<MarketDailyQuote>> entry : quotesByMonth.entrySet()) {
            String monthKey = entry.getKey();
            List<MarketDailyQuote> monthQuotes = entry.getValue();

            // Sort deterministically by tradeDate ASC, then ticker ASC
            monthQuotes.sort(Comparator.comparing(MarketDailyQuote::getTradeDate)
                    .thenComparing(MarketDailyQuote::getTicker));

            String fileName = String.format("V4_%s__seed_market_daily_quotes_%s.sql", monthKey, monthKey);
            Path filePath = targetDir.resolve(fileName);

            StringBuilder sql = new StringBuilder();
            sql.append("-- ").append(fileName).append("\n");
            sql.append("-- Seed official daily market quotes for ").append(monthKey).append(" (Auto-generated from Yahoo Finance)\n");
            sql.append("-- Total records: ").append(monthQuotes.size()).append("\n\n");

            for (int i = 0; i < monthQuotes.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, monthQuotes.size());
                List<MarketDailyQuote> batch = monthQuotes.subList(i, end);

                sql.append("INSERT INTO market_daily_quote (id, asset_id, benchmark_id, ticker, trade_date, open_price, high_price, low_price, close_price, volume_shares, trade_value_twd, net_asset_value, discount_premium_percentage)\n");
                sql.append("VALUES\n");

                for (int j = 0; j < batch.size(); j++) {
                    MarketDailyQuote q = batch.get(j);
                    sql.append(formatQuoteRow(q));
                    if (j < batch.size() - 1) {
                        sql.append(",\n");
                    } else {
                        sql.append(";\n\n");
                    }
                }
            }

            Files.writeString(filePath, sql.toString(), StandardCharsets.UTF_8);
            log.info("Generated Flyway migration: {} ({} quotes, {} bytes)",
                    fileName, monthQuotes.size(), Files.size(filePath));
            assertThat(Files.exists(filePath)).isTrue();
            assertThat(Files.size(filePath)).isGreaterThan(100L);
        }

        log.info("All {} monthly Flyway V4 migration files successfully generated!", quotesByMonth.size());
    }

    private String formatQuoteRow(MarketDailyQuote q) {
        String idStr = q.getId().toString();
        String assetIdStr = q.getAssetId() != null ? "'" + q.getAssetId() + "'" : "NULL";
        String benchmarkIdStr = q.getBenchmarkId() != null ? "'" + q.getBenchmarkId() + "'" : "NULL";
        String ticker = q.getTicker().replace("'", "''");
        String tradeDate = "TIMESTAMP '" + q.getTradeDate().format(SQL_TIMESTAMP_FMT) + "'";
        String open = q.getOpenPrice() != null ? q.getOpenPrice().setScale(4, RoundingMode.HALF_UP).toPlainString() : "NULL";
        String high = q.getHighPrice() != null ? q.getHighPrice().setScale(4, RoundingMode.HALF_UP).toPlainString() : "NULL";
        String low = q.getLowPrice() != null ? q.getLowPrice().setScale(4, RoundingMode.HALF_UP).toPlainString() : "NULL";
        String close = q.getClosePrice().setScale(4, RoundingMode.HALF_UP).toPlainString();
        String volume = q.getVolumeShares() != null ? q.getVolumeShares().toString() : "0";
        String tradeValue = q.getTradeValueTwd() != null ? q.getTradeValueTwd().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
        String nav = q.getNetAssetValue() != null ? q.getNetAssetValue().setScale(4, RoundingMode.HALF_UP).toPlainString() : "NULL";
        String discPrem = q.getDiscountPremiumPercentage() != null ? q.getDiscountPremiumPercentage().setScale(4, RoundingMode.HALF_UP).toPlainString() : "NULL";

        return String.format("    ('%s', %s, %s, '%s', %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                idStr, assetIdStr, benchmarkIdStr, ticker, tradeDate, open, high, low, close, volume, tradeValue, nav, discPrem);
    }
}
