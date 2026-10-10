package com.alphaharvester.persistence;

import com.alphaharvester.adapter.out.persistence.BenchmarkIndexRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RepositoryIntegrationTest {

    @Autowired
    private BenchmarkIndexRepository benchmarkRepository;

    @Autowired
    private GlobalAssetMetadataRepository metadataRepository;

    @Autowired
    private MarketDailyQuoteRepository quoteRepository;

    @Test
    @DisplayName("Should verify Flyway seeded exactly 9 pre-defined benchmark indices")
    void shouldVerifyFlywaySeededBenchmarks() {
        StepVerifier.create(benchmarkRepository.findAll().collectList())
                .assertNext(benchmarks -> {
                    assertThat(benchmarks).hasSize(9);
                    assertThat(benchmarks).extracting("ticker")
                            .contains("^TWII", "^GSPC", "^NDX", "^SOX", "^N225", "^VIX", "^VXN", "^MOVE", "FEAR_GREED");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should insert and query GlobalAssetMetadata with UUID callback and snake_case mapping")
    void shouldInsertAndQueryGlobalAssetMetadata() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                null,
                "009999",
                "測試卓越ETF",
                now,
                "臺灣50指數",
                null,
                null,
                null,
                null
        );

        StepVerifier.create(metadataRepository.save(asset))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getUpdatedAt()).isNotNull();
                    assertThat(saved.getTicker()).isEqualTo("009999");
                    assertThat(saved.getUnderlyingIndex()).isEqualTo("臺灣50指數");
                })
                .verifyComplete();

        StepVerifier.create(metadataRepository.findByTicker("009999"))
                .assertNext(found -> {
                    assertThat(found.getName()).isEqualTo("測試卓越ETF");
                    assertThat(found.getListingDate()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should verify Flyway V3 seeded ETF metadata including shares outstanding, NAV, and fund size TWD")
    void shouldVerifyFlywaySeededEtfMetadataWithAum() {
        StepVerifier.create(metadataRepository.findByTicker("0050"))
                .assertNext(etf0050 -> {
                    assertThat(etf0050).isNotNull();
                    assertThat(etf0050.getName()).contains("元大台灣卓越50");
                    assertThat(etf0050.getSharesOutstanding()).isNotNull().isGreaterThan(0L);
                    assertThat(etf0050.getNetAssetValue()).isNotNull().isGreaterThan(BigDecimal.ZERO);
                    assertThat(etf0050.getFundSizeTwd()).isNotNull().isGreaterThan(new BigDecimal("100000000000")); // > 100B TWD
                })
                .verifyComplete();

        StepVerifier.create(metadataRepository.findAll().collectList())
                .assertNext(all -> {
                    assertThat(all).hasSizeGreaterThanOrEqualTo(300);
                    long withAum = all.stream().filter(a -> a.getFundSizeTwd() != null).count();
                    assertThat(withAum).isGreaterThanOrEqualTo(250);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should insert and query MarketDailyQuote correctly")
    void shouldInsertAndQueryMarketDailyQuote() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        MarketDailyQuote quote = new MarketDailyQuote(
                null,
                null,
                null,
                "^TEST",
                now,
                new BigDecimal("22500.0"),
                new BigDecimal("22800.0"),
                new BigDecimal("22400.0"),
                new BigDecimal("22750.0"),
                1000000L,
                new BigDecimal("400000000000")
        );

        StepVerifier.create(quoteRepository.save(quote))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getClosePrice()).isEqualByComparingTo(new BigDecimal("22750.0"));
                })
                .verifyComplete();

        StepVerifier.create(quoteRepository.findByTickerOrderByTradeDateDesc("^TEST"))
                .assertNext(found -> {
                    assertThat(found.getTicker()).isEqualTo("^TEST");
                    assertThat(found.getClosePrice()).isEqualByComparingTo(new BigDecimal("22750.0"));
                })
                .verifyComplete();

        // Verify date-filtered index pushdown methods
        MarketDailyQuote oldQuote = new MarketDailyQuote(
                null, null, null, "^TEST", now.minusYears(2),
                new BigDecimal("15000.0"), new BigDecimal("15100.0"), new BigDecimal("14900.0"),
                new BigDecimal("15050.0"), 500000L, new BigDecimal("200000000")
        );
        MarketDailyQuote midQuote = new MarketDailyQuote(
                null, null, null, "^TEST", now.minusMonths(6),
                new BigDecimal("19000.0"), new BigDecimal("19200.0"), new BigDecimal("18900.0"),
                new BigDecimal("19100.0"), 800000L, new BigDecimal("300000000")
        );
        quoteRepository.save(oldQuote).block();
        quoteRepository.save(midQuote).block();

        // 1. findByTickerAndTradeDateBetweenOrderByTradeDateDesc (should return midQuote and quote, excluding oldQuote)
        StepVerifier.create(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^TEST", now.minusYears(1), now.plusDays(1)))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now.minusMonths(6)))
                .verifyComplete();

        // 2. findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc (should return midQuote and quote, excluding oldQuote)
        StepVerifier.create(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc("^TEST", now.minusYears(1)))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now.minusMonths(6)))
                .verifyComplete();

        // 3. findFirstByTickerOrderByTradeDateDesc (should return latest quote)
        StepVerifier.create(quoteRepository.findFirstByTickerOrderByTradeDateDesc("^TEST"))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now))
                .verifyComplete();

        // 4. findTop240ByTickerOrderByTradeDateDesc (should return all 3 quotes descending)
        StepVerifier.create(quoteRepository.findTop240ByTickerOrderByTradeDateDesc("^TEST"))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now.minusMonths(6)))
                .assertNext(q -> assertThat(q.getTradeDate()).isEqualTo(now.minusYears(2)))
                .verifyComplete();
    }

    @Autowired
    private com.alphaharvester.adapter.out.persistence.DataFeedSyncWatermarkRepository watermarkRepository;

    @Test
    @DisplayName("Should verify Flyway seeded data feed watermarks and query by feed name")
    void shouldVerifyFlywaySeededWatermarks() {
        StepVerifier.create(watermarkRepository.findAll().collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(8);
                    assertThat(list).extracting("feedName")
                            .contains("TAIWAN_ETF_QUOTES", "GLOBAL_BENCHMARKS", "CNN_FEAR_GREED",
                                    "MACRO_YIELD_SNAPSHOT", "TWSE_DCA_RANKINGS", "TWSE_ETF_METADATA",
                                    "MONTHLY_TOP_LIST", "DIVIDENDS_AND_SPLITS");
                })
                .verifyComplete();

        StepVerifier.create(watermarkRepository.findByFeedName("GLOBAL_BENCHMARKS"))
                .assertNext(wm -> {
                    assertThat(wm.getFeedName()).isEqualTo("GLOBAL_BENCHMARKS");
                    assertThat(wm.getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();

        StepVerifier.create(watermarkRepository.findByFeedName("TWSE_DCA_RANKINGS"))
                .assertNext(wm -> {
                    assertThat(wm.getFeedName()).isEqualTo("TWSE_DCA_RANKINGS");
                    assertThat(wm.getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();

        StepVerifier.create(watermarkRepository.findByFeedName("DIVIDENDS_AND_SPLITS"))
                .assertNext(wm -> {
                    assertThat(wm.getFeedName()).isEqualTo("DIVIDENDS_AND_SPLITS");
                    assertThat(wm.getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();

        StepVerifier.create(watermarkRepository.findByFeedName("MONTHLY_TOP_LIST"))
                .assertNext(wm -> {
                    assertThat(wm.getFeedName()).isEqualTo("MONTHLY_TOP_LIST");
                    assertThat(wm.getStatus()).isEqualTo("PASS");
                })
                .verifyComplete();
    }

    @Autowired
    private com.alphaharvester.adapter.out.persistence.CorporateActionRepository corporateActionRepository;

    @Autowired
    private com.alphaharvester.adapter.out.persistence.DividendAnnouncementRepository dividendRepository;

    @Autowired
    private com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository scoreRepository;

    @Autowired
    private com.alphaharvester.adapter.out.persistence.GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository;

    @Test
    @DisplayName("Should verify Flyway V7 seeded corporate actions (0050, 0052 splits) and dividends")
    void shouldVerifyFlywaySeededCorporateActionsAndDividends() {
        StepVerifier.create(corporateActionRepository.findAll().collectList())
                .assertNext(splits -> {
                    assertThat(splits).hasSize(2);
                    assertThat(splits).extracting("ticker").containsExactlyInAnyOrder("0050", "0052");
                })
                .verifyComplete();

        StepVerifier.create(dividendRepository.count())
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(1000L))
                .verifyComplete();

        LocalDateTime octStart = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime octEnd = LocalDateTime.of(2025, 10, 31, 23, 59, 59);
        StepVerifier.create(dividendRepository.findByExDateBetweenOrPaymentDateBetweenOrderByExDateAsc(octStart, octEnd, octStart, octEnd).collectList())
                .assertNext(list -> {
                    assertThat(list).isNotEmpty();
                    assertThat(list.stream().anyMatch(d -> "0056".equals(d.getTicker()))).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should verify Flyway V8 and V9 seeded monthly top list scores and pairwise matrices")
    void shouldVerifyFlywaySeededMonthlyTopListAndPairwiseMatrix() {
        // V8 (2026-09: 59 scores, 1231 matrices) + V9 (2026-10: 57 scores, 1091 matrices)
        StepVerifier.create(scoreRepository.count())
                .assertNext(count -> assertThat(count).isEqualTo(116L))
                .verifyComplete();

        StepVerifier.create(pairwiseMatrixRepository.count())
                .assertNext(count -> assertThat(count).isEqualTo(2322L))
                .verifyComplete();
    }
}
