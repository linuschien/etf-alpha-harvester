package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.application.service.DataCompletenessGatekeeperService;
import com.alphaharvester.application.service.GlobalAssetScoreEvaluationService;
import com.alphaharvester.application.service.MarketDataSyncService;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.SyncScope;
import com.alphaharvester.domain.model.TaxTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketDataSyncServiceTest {

    @Mock private ExternalMarketDataPort externalMarketDataPort;
    @Mock private GlobalAssetMetadataRepository metadataRepository;
    @Mock private BenchmarkIndexRepository benchmarkRepository;
    @Mock private MarketDailyQuoteRepository quoteRepository;
    @Mock private MacroYieldSnapshotRepository macroYieldRepository;
    @Mock private DcaPopularityRankRepository dcaRankRepository;
    @Mock private DividendAnnouncementRepository dividendRepository;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Mock private GlobalAssetScoreEvaluationService scoreEvaluationService;
    @Mock private DataFeedSyncWatermarkRepository watermarkRepository;
    @Mock private DataCompletenessGatekeeperService gatekeeperService;

    private MarketDataSyncService syncService;

    @BeforeEach
    void setUp() {
        when(gatekeeperService.checkCompleteness()).thenReturn(
                Mono.just(new com.alphaharvester.application.dto.GatekeeperReport(
                        "PASS", "OK", LocalDateTime.now(), 1, true, java.util.List.of()
                ))
        );
        syncService = new MarketDataSyncService(
                externalMarketDataPort,
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, dcaRankRepository, dividendRepository,
                corporateActionRepository, scoreEvaluationService,
                watermarkRepository,
                gatekeeperService
        );
        when(watermarkRepository.findByFeedName(anyString())).thenReturn(Mono.empty());
        when(watermarkRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(externalMarketDataPort.fetchEtfMasterUniverse()).thenReturn(Flux.empty());
        when(externalMarketDataPort.fetchTaiwanEtfDailyQuotes(any())).thenReturn(Flux.empty());
        when(externalMarketDataPort.fetchBenchmarkQuotes(anyString())).thenReturn(Flux.empty());
        when(externalMarketDataPort.fetchCnnSentimentQuote()).thenReturn(Mono.empty());
        when(externalMarketDataPort.fetchLatestMacroYield()).thenReturn(Mono.empty());
        when(externalMarketDataPort.fetchDcaPopularityRanks(anyInt(), anyInt())).thenReturn(Flux.empty());
        when(externalMarketDataPort.fetchDividendsAndSplits(anyString(), anyString()))
                .thenReturn(Mono.just(new ExternalMarketDataPort.DividendsAndSplits(List.of(), List.of())));
        when(metadataRepository.findAll()).thenReturn(Flux.empty());
    }

    @Test
    @DisplayName("Should sync market data successfully with scope ALL via ExternalMarketDataPort")
    void shouldSyncMarketDataWithScopeAll() {
        UUID id50 = UUID.randomUUID();
        UUID id720b = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetMetadata asset50 = new GlobalAssetMetadata(id50, "0050", "元大台灣50", now, null,
                1, now, now, null);
        GlobalAssetMetadata asset720b = new GlobalAssetMetadata(id720b, "00720B", "元大投資級公司債", now, null,
                1, now, now, null);

        MarketDailyQuote quote50 = new MarketDailyQuote(null, id50, null, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 2000000L, new BigDecimal("376000000"),
                new BigDecimal("188.10"), new BigDecimal("-0.05"));

        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(null, now, new BigDecimal("5.25"),
                new BigDecimal("4.28"), new BigDecimal("4.58"), new BigDecimal("-0.15"));

        DcaPopularityRank rank50 = new DcaPopularityRank(null, null, "0050", now.getYear(), now.getMonthValue(), 1, 1280000);
        DividendAnnouncement div = new DividendAnnouncement(null, null, "00720B", now.plusDays(10), now.plusDays(30), new BigDecimal("0.48"), TaxTag.OVERSEAS_76W);

        // Mock external port calls
        when(externalMarketDataPort.fetchEtfMasterUniverse()).thenReturn(Flux.just(asset50, asset720b));
        when(externalMarketDataPort.fetchTaiwanEtfDailyQuotes(any())).thenReturn(Flux.just(quote50));
        when(externalMarketDataPort.fetchBenchmarkQuotes(anyString())).thenReturn(Flux.empty());
        when(externalMarketDataPort.fetchCnnSentimentQuote()).thenReturn(Mono.empty());
        when(externalMarketDataPort.fetchLatestMacroYield()).thenReturn(Mono.just(snapshot));
        when(externalMarketDataPort.fetchDcaPopularityRanks(anyInt(), anyInt())).thenReturn(Flux.just(rank50));
        when(externalMarketDataPort.fetchDividendsAndSplits(eq("00720B"), anyString()))
                .thenReturn(Mono.just(new ExternalMarketDataPort.DividendsAndSplits(List.of(div), List.of())));

        // Mock repository calls
        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(asset50));
        when(metadataRepository.findByTicker("00720B")).thenReturn(Mono.just(asset720b));
        when(metadataRepository.findAll()).thenReturn(Flux.just(asset720b));
        when(metadataRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(quoteRepository.findByTickerAndTradeDate(any(), any())).thenReturn(Mono.empty());
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(anyString())).thenReturn(Mono.just(quote50));
        when(quoteRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(macroYieldRepository.findByRecordDate(any())).thenReturn(Mono.empty());
        when(macroYieldRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(dcaRankRepository.findByTickerAndRankingYearAndRankingMonth(any(), any(), any())).thenReturn(Mono.empty());
        when(dcaRankRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(dividendRepository.findByTickerAndExDate(any(), any())).thenReturn(Mono.empty());
        when(dividendRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(corporateActionRepository.findByTickerAndEffectiveDate(any(), any())).thenReturn(Mono.empty());
        when(corporateActionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(scoreEvaluationService.evaluateGlobalAssetScores()).thenReturn(Mono.just(
                new GlobalAssetScoreEvaluationResponse("SUCCESS", "Evaluated", now.toString(), 2, 1, 0, 1)
        ));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.ALL, true);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().etfAssetsCount()).isEqualTo(2);
                    assertThat(res.syncedRecords().dailyQuotesCount()).isEqualTo(1);
                    assertThat(res.syncedRecords().macroYieldSnapshotsCount()).isEqualTo(1);
                    assertThat(res.syncedRecords().dcaPopularityRanksCount()).isEqualTo(1);
                    assertThat(res.syncedRecords().dividendAnnouncementsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should detect multi-day gap and automatically backfill missing trading bars from Yahoo Finance")
    void shouldAutoBackfillWhenMultiDayGapDetected() {
        LocalDateTime now = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", now.minusYears(5), null,
                1, now, now, null);

        // Previous recorded date is 5 days ago (simulating shutdown/offline outage)
        MarketDailyQuote oldQuote = new MarketDailyQuote(null, id, null, "0050", now.minusDays(5),
                new BigDecimal("180.0"), new BigDecimal("182.0"), new BigDecimal("179.0"),
                new BigDecimal("181.0"), 1000000L, BigDecimal.ZERO, null, null);

        MarketDailyQuote todayQuote = new MarketDailyQuote(null, id, null, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("187.0"), new BigDecimal("184.0"),
                new BigDecimal("186.0"), 1200000L, BigDecimal.ZERO, null, null);

        MarketDailyQuote backfillQuote = new MarketDailyQuote(null, id, null, "0050", now.minusDays(2),
                new BigDecimal("182.0"), new BigDecimal("184.0"), new BigDecimal("181.0"),
                new BigDecimal("183.0"), 1100000L, BigDecimal.ZERO, null, null);

        DataFeedSyncWatermark watermark = new DataFeedSyncWatermark(
                UUID.randomUUID(), MarketDataSyncService.WATERMARK_TAIWAN_ETF_QUOTES, now.minusDays(5), now.minusDays(5), 10, "SUCCESS", null, now.minusDays(5)
        );
        when(watermarkRepository.findByFeedName(MarketDataSyncService.WATERMARK_TAIWAN_ETF_QUOTES)).thenReturn(Mono.just(watermark));

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(externalMarketDataPort.fetchTaiwanEtfDailyQuotes(any())).thenReturn(Flux.just(todayQuote));
        when(externalMarketDataPort.fetchBenchmarkQuotes(anyString())).thenReturn(Flux.empty());
        when(externalMarketDataPort.fetchCnnSentimentQuote()).thenReturn(Mono.empty());

        // When watermark gap is detected (> 1 or 3 days), Yahoo historical backfill is triggered for candidate ETFs
        when(externalMarketDataPort.fetchHistoricalQuotes(eq("0050"), anyString())).thenReturn(Flux.just(backfillQuote));
        when(quoteRepository.findByTickerAndTradeDate(any(), any())).thenReturn(Mono.empty());
        when(quoteRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.QUOTES, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    // 1 today quote + 1 backfill quote caught up = 2 quotes synced!
                    assertThat(res.syncedRecords().dailyQuotesCount()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should independently check and update watermarks for all 6 data feeds")
    void shouldIndependentlyCheckAndUpdateAllSixWatermarks() {
        LocalDateTime now = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", now, null,
                1, now, now, null);

        MarketDailyQuote etfQuote = new MarketDailyQuote(null, id, null, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 2000000L, new BigDecimal("376000000"), null, null);
        MarketDailyQuote benchQuote = new MarketDailyQuote(null, null, null, "^TWII", now,
                new BigDecimal("22000.0"), new BigDecimal("22100.0"), new BigDecimal("21950.0"),
                new BigDecimal("22050.0"), 5000000000L, BigDecimal.ZERO, null, null);
        MarketDailyQuote cnnQuote = new MarketDailyQuote(null, null, null, "FEAR_GREED", now,
                new BigDecimal("55.0"), new BigDecimal("55.0"), new BigDecimal("55.0"),
                new BigDecimal("55.0"), 0L, BigDecimal.ZERO, null, null);

        MacroYieldSnapshot yieldSnapshot = new MacroYieldSnapshot(null, now, new BigDecimal("5.25"),
                new BigDecimal("4.28"), new BigDecimal("4.58"), new BigDecimal("-0.15"));
        DcaPopularityRank dcaRank = new DcaPopularityRank(null, null, "0050", now.getYear(), now.getMonthValue(), 1, 1000);

        when(externalMarketDataPort.fetchEtfMasterUniverse()).thenReturn(Flux.just(asset));
        when(externalMarketDataPort.fetchTaiwanEtfDailyQuotes(any())).thenReturn(Flux.just(etfQuote));
        when(externalMarketDataPort.fetchBenchmarkQuotes(anyString())).thenReturn(Flux.just(benchQuote));
        when(externalMarketDataPort.fetchCnnSentimentQuote()).thenReturn(Mono.just(cnnQuote));
        when(externalMarketDataPort.fetchLatestMacroYield()).thenReturn(Mono.just(yieldSnapshot));
        when(externalMarketDataPort.fetchDcaPopularityRanks(anyInt(), anyInt())).thenReturn(Flux.just(dcaRank));

        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(asset));
        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(metadataRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(quoteRepository.findByTickerAndTradeDate(any(), any())).thenReturn(Mono.empty());
        when(quoteRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(macroYieldRepository.findByRecordDate(any())).thenReturn(Mono.empty());
        when(macroYieldRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(dcaRankRepository.findByTickerAndRankingYearAndRankingMonth(any(), any(), any())).thenReturn(Mono.empty());
        when(dcaRankRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.ALL, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().etfAssetsCount()).isEqualTo(1);
                    // 1 ETF quote + 1 benchmark quote + 1 CNN quote = 3 daily quotes!
                    assertThat(res.syncedRecords().dailyQuotesCount()).isEqualTo(3);
                    assertThat(res.syncedRecords().macroYieldSnapshotsCount()).isEqualTo(1);
                    assertThat(res.syncedRecords().dcaPopularityRanksCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should HALT pipeline and block downstream evaluation when gatekeeper fails completeness check")
    void shouldHaltPipelineWhenGatekeeperDetectsIncompleteness() {
        when(gatekeeperService.checkCompleteness()).thenReturn(
                Mono.just(new com.alphaharvester.application.dto.GatekeeperReport(
                        "HALT", "市場數據採集未齊全", LocalDateTime.now(), 0, false, java.util.List.of("未找到最新宏觀殖利率快照")
                ))
        );
        when(externalMarketDataPort.fetchEtfMasterUniverse()).thenReturn(Flux.empty());

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.METADATA, true);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("HALT");
                    assertThat(res.message()).contains("安全暫停");
                    assertThat(res.gatekeeperReport()).isNotNull();
                    assertThat(res.gatekeeperReport().isPassed()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should skip dividends and splits synchronization when already synced in current month (Scope ALL)")
    void shouldSkipDividendsAndSplitsWhenAlreadySyncedInCurrentMonth() {
        LocalDateTime now = LocalDateTime.now();
        DataFeedSyncWatermark watermark = new DataFeedSyncWatermark(
                UUID.randomUUID(), MarketDataSyncService.WATERMARK_DIVIDENDS_AND_SPLITS,
                now.minusDays(2), now.minusDays(2), 25, "SUCCESS", null, now.minusDays(2)
        );
        when(watermarkRepository.findByFeedName(MarketDataSyncService.WATERMARK_DIVIDENDS_AND_SPLITS))
                .thenReturn(Mono.just(watermark));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.ALL, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    // Dividends and splits should be 0 because watermark indicates already synced this month
                    assertThat(res.syncedRecords().dividendAnnouncementsCount()).isEqualTo(0);
                    assertThat(res.syncedRecords().corporateActionsCount()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should execute dividends and splits synchronization unconditionally when scope is DIVIDENDS_AND_SPLITS")
    void shouldExecuteDividendsAndSplitsWhenScopeExplicit() {
        LocalDateTime now = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", now, null, 1, now, now, null);
        DividendAnnouncement div = new DividendAnnouncement(null, null, "0050", now.plusDays(5), now.plusDays(30), new BigDecimal("1.5"), TaxTag.DOMESTIC_54C);
        CorporateAction split = new CorporateAction(null, null, "0050", com.alphaharvester.domain.model.CorporateActionType.SPLIT, now, 4, 1);

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(externalMarketDataPort.fetchDividendsAndSplits(eq("0050"), anyString()))
                .thenReturn(Mono.just(new ExternalMarketDataPort.DividendsAndSplits(List.of(div), List.of(split))));
        when(dividendRepository.findByTickerAndExDate(any(), any())).thenReturn(Mono.empty());
        when(dividendRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(corporateActionRepository.findByTickerAndEffectiveDate(any(), any())).thenReturn(Mono.empty());
        when(corporateActionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.DIVIDENDS_AND_SPLITS, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().dividendAnnouncementsCount()).isEqualTo(1);
                    assertThat(res.syncedRecords().corporateActionsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should skip metadata synchronization when already synced in current month (Scope ALL)")
    void shouldSkipMetadataWhenAlreadySyncedInCurrentMonth() {
        LocalDateTime now = LocalDateTime.now();
        DataFeedSyncWatermark watermark = new DataFeedSyncWatermark(
                UUID.randomUUID(), MarketDataSyncService.WATERMARK_TWSE_ETF_METADATA,
                now.minusDays(3), now.minusDays(3), 150, "SUCCESS", null, now.minusDays(3)
        );
        when(watermarkRepository.findByFeedName(MarketDataSyncService.WATERMARK_TWSE_ETF_METADATA))
                .thenReturn(Mono.just(watermark));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.ALL, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().etfAssetsCount()).isEqualTo(0);
                    verify(externalMarketDataPort, never()).fetchEtfMasterUniverse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should execute metadata synchronization when scope is explicitly METADATA even if already synced")
    void shouldExecuteMetadataWhenScopeExplicit() {
        LocalDateTime now = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", now, null, 1, now, now, null);
        when(externalMarketDataPort.fetchEtfMasterUniverse()).thenReturn(Flux.just(asset));
        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(asset));
        when(metadataRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.METADATA, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().etfAssetsCount()).isEqualTo(1);
                    verify(externalMarketDataPort).fetchEtfMasterUniverse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should skip DCA rankings synchronization when already synced in current month with records (Scope ALL)")
    void shouldSkipDcaRanksWhenAlreadySyncedInCurrentMonth() {
        LocalDateTime now = LocalDateTime.now();
        java.time.YearMonth reportMonth = (now.getDayOfMonth() >= 11)
                ? java.time.YearMonth.from(now).minusMonths(1)
                : java.time.YearMonth.from(now).minusMonths(2);
        LocalDateTime recordDate = reportMonth.atEndOfMonth().atStartOfDay();
        DataFeedSyncWatermark watermark = new DataFeedSyncWatermark(
                UUID.randomUUID(), MarketDataSyncService.WATERMARK_TWSE_DCA_RANKINGS,
                now.minusDays(5), recordDate, 20, "SUCCESS", null, now.minusDays(5)
        );
        when(watermarkRepository.findByFeedName(MarketDataSyncService.WATERMARK_TWSE_DCA_RANKINGS))
                .thenReturn(Mono.just(watermark));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.ALL, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().dcaPopularityRanksCount()).isEqualTo(0);
                    verify(externalMarketDataPort, never()).fetchDcaPopularityRanks(anyInt(), anyInt());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should retry DCA rankings synchronization when watermark has 0 records synced this month")
    void shouldRetryDcaRanksWhenWatermarkHasZeroRecords() {
        LocalDateTime now = LocalDateTime.now();
        java.time.YearMonth reportMonth = (now.getDayOfMonth() >= 11)
                ? java.time.YearMonth.from(now).minusMonths(1)
                : java.time.YearMonth.from(now).minusMonths(2);
        LocalDateTime recordDate = reportMonth.atEndOfMonth().atStartOfDay();
        DataFeedSyncWatermark watermark = new DataFeedSyncWatermark(
                UUID.randomUUID(), MarketDataSyncService.WATERMARK_TWSE_DCA_RANKINGS,
                now.minusDays(2), recordDate, 0, "SUCCESS", null, now.minusDays(2)
        );
        when(watermarkRepository.findByFeedName(MarketDataSyncService.WATERMARK_TWSE_DCA_RANKINGS))
                .thenReturn(Mono.just(watermark));

        DcaPopularityRank rank = new DcaPopularityRank(null, null, "0050", reportMonth.getYear(), reportMonth.getMonthValue(), 1, 1000000);
        when(externalMarketDataPort.fetchDcaPopularityRanks(reportMonth.getYear(), reportMonth.getMonthValue())).thenReturn(Flux.just(rank));
        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.empty());
        when(dcaRankRepository.findByTickerAndRankingYearAndRankingMonth(any(), any(), any())).thenReturn(Mono.empty());
        when(dcaRankRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.ALL, false);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().dcaPopularityRanksCount()).isEqualTo(1);
                    verify(externalMarketDataPort).fetchDcaPopularityRanks(reportMonth.getYear(), reportMonth.getMonthValue());
                })
                .verifyComplete();
    }
}
