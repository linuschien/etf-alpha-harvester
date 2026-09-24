package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.service.GlobalAssetScoreEvaluationService;
import com.alphaharvester.application.service.MarketDataSyncService;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.SyncScope;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketDataSyncServiceTest {

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
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, dcaRankRepository, dividendRepository,
                corporateActionRepository, scoreEvaluationService
        );
    }

    @Test
    @DisplayName("Should sync market data successfully with scope ALL")
    void shouldSyncMarketDataWithScopeAll() {
        UUID id50 = UUID.randomUUID();
        UUID id720b = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetMetadata asset50 = new GlobalAssetMetadata(id50, "0050", "元大台灣50", now, null, null,
                null, null, CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null);
        GlobalAssetMetadata asset720b = new GlobalAssetMetadata(id720b, "00720B", "元大投資級公司債", now, null, null,
                null, null, CandidateAssetClass.DEFENSIVE, DistributionFrequency.QUARTERLY, 1, now, now, null);

        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(asset50));
        when(metadataRepository.findByTicker("00720B")).thenReturn(Mono.just(asset720b));
        when(metadataRepository.findByTicker(any())).thenReturn(Mono.empty());
        when(metadataRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(quoteRepository.findByTickerAndTradeDate(any(), any())).thenReturn(Mono.empty());
        when(quoteRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(macroYieldRepository.findByRecordDate(any())).thenReturn(Mono.empty());
        when(macroYieldRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(dcaRankRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(dividendRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(scoreEvaluationService.evaluateGlobalAssetScores()).thenReturn(Mono.just(
                new GlobalAssetScoreEvaluationResponse("SUCCESS", "Evaluated", now.toString(), 5, 2, 2, 1)
        ));

        MarketDataSyncRequest req = new MarketDataSyncRequest(SyncScope.ALL, true);

        StepVerifier.create(syncService.syncMarketData(req))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.syncedRecords().etfAssetsCount()).isGreaterThan(0);
                    assertThat(res.syncedRecords().dailyQuotesCount()).isGreaterThan(0);
                    assertThat(res.syncedRecords().macroYieldSnapshotsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }
}
