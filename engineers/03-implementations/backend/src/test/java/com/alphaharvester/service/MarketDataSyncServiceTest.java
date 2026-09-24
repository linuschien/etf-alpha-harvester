package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.port.out.ExternalMarketDataPort;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private MarketDataSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new MarketDataSyncService(
                externalMarketDataPort,
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, dcaRankRepository, dividendRepository,
                corporateActionRepository, scoreEvaluationService
        );
    }

    @Test
    @DisplayName("Should sync market data successfully with scope ALL via ExternalMarketDataPort")
    void shouldSyncMarketDataWithScopeAll() {
        UUID id50 = UUID.randomUUID();
        UUID id720b = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetMetadata asset50 = new GlobalAssetMetadata(id50, "0050", "元大台灣50", now, null, null,
                null, null, CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null);
        GlobalAssetMetadata asset720b = new GlobalAssetMetadata(id720b, "00720B", "元大投資級公司債", now, null, null,
                null, null, CandidateAssetClass.DEFENSIVE, DistributionFrequency.QUARTERLY, 1, now, now, null);

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
        when(externalMarketDataPort.fetchDailyQuotes(any())).thenReturn(Flux.just(quote50));
        when(externalMarketDataPort.fetchLatestMacroYield()).thenReturn(Mono.just(snapshot));
        when(externalMarketDataPort.fetchDcaPopularityRanks(anyInt(), anyInt())).thenReturn(Flux.just(rank50));
        when(externalMarketDataPort.fetchDividendAnnouncements("00720B")).thenReturn(Flux.just(div));
        when(externalMarketDataPort.fetchCorporateActions(any())).thenReturn(Flux.empty());

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
        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", now.minusYears(5), null, null,
                null, null, CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null);

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

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(externalMarketDataPort.fetchDailyQuotes(any())).thenReturn(Flux.just(todayQuote));
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc("0050")).thenReturn(Mono.just(oldQuote));

        // When gap is detected (> 1 or 3 days), Yahoo historical backfill is triggered
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
}
