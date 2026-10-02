package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.*;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.CorporateActionType;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.TaxTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Example;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalAssetQueryServiceTest {

    @Mock private GlobalAssetMetadataRepository metadataRepository;
    @Mock private BenchmarkIndexRepository benchmarkRepository;
    @Mock private MarketDailyQuoteRepository quoteRepository;
    @Mock private MacroYieldSnapshotRepository macroYieldRepository;
    @Mock private GlobalAssetScoreRepository scoreRepository;
    @Mock private DcaPopularityRankRepository dcaRankRepository;
    @Mock private DividendAnnouncementRepository dividendRepository;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Mock private GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository;
    @Mock private DataFeedSyncWatermarkRepository watermarkRepository;

    private GlobalAssetQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new GlobalAssetQueryService(
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, scoreRepository, dcaRankRepository,
                dividendRepository, corporateActionRepository, pairwiseMatrixRepository,
                watermarkRepository
        );
    }

    @Test
    @DisplayName("Should query GlobalAssetMetadata with and without filter")
    void shouldQueryGlobalAssetMetadata() {
        UUID id = UUID.randomUUID();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", LocalDateTime.now(),
                "臺灣50", 1, LocalDateTime.now(), LocalDateTime.now(), null);

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(metadataRepository.findById(id)).thenReturn(Mono.just(asset));
        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(asset));
        when(metadataRepository.findAll(any(Example.class))).thenReturn(Flux.just(asset));

        StepVerifier.create(queryService.listGlobalAssets(null))
                .assertNext(a -> assertThat(a.getTicker()).isEqualTo("0050"))
                .verifyComplete();

        StepVerifier.create(queryService.listGlobalAssets(new GlobalAssetFilterInput("0050")))
                .assertNext(a -> assertThat(a.getTicker()).isEqualTo("0050"))
                .verifyComplete();

        StepVerifier.create(queryService.getGlobalAssetById(id))
                .assertNext(a -> assertThat(a.getId()).isEqualTo(id))
                .verifyComplete();

        StepVerifier.create(queryService.getGlobalAssetByTicker("0050"))
                .assertNext(a -> assertThat(a.getName()).isEqualTo("元大台灣50"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query BenchmarkIndex correctly")
    void shouldQueryBenchmarkIndex() {
        UUID id = UUID.randomUUID();
        BenchmarkIndex benchmark = new BenchmarkIndex(id, "^TWII", "加權指數", "TW", "台股大盤", 1, LocalDateTime.now(), LocalDateTime.now(), null);

        when(benchmarkRepository.findAll()).thenReturn(Flux.just(benchmark));
        when(benchmarkRepository.findById(id)).thenReturn(Mono.just(benchmark));
        when(benchmarkRepository.findByTicker("^TWII")).thenReturn(Mono.just(benchmark));

        StepVerifier.create(queryService.listBenchmarkIndices())
                .assertNext(b -> assertThat(b.getTicker()).isEqualTo("^TWII"))
                .verifyComplete();

        StepVerifier.create(queryService.getBenchmarkIndexById(id))
                .assertNext(b -> assertThat(b.getId()).isEqualTo(id))
                .verifyComplete();

        StepVerifier.create(queryService.getBenchmarkIndexByTicker("^TWII"))
                .assertNext(b -> assertThat(b.getRegion()).isEqualTo("TW"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query MarketDailyQuote and time series")
    void shouldQueryMarketDailyQuote() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        MarketDailyQuote quote = new MarketDailyQuote(id, null, null, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 1000000L, new BigDecimal("188000000"));

        when(quoteRepository.findAll()).thenReturn(Flux.just(quote));
        when(quoteRepository.findById(id)).thenReturn(Mono.just(quote));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.just(quote));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any())).thenReturn(Flux.just(quote));

        StepVerifier.create(queryService.listMarketDailyQuotes(null))
                .assertNext(q -> assertThat(q.getTicker()).isEqualTo("0050"))
                .verifyComplete();

        StepVerifier.create(queryService.getQuoteTimeSeries("0050", "2026-09-01", "2026-09-23"))
                .assertNext(q -> assertThat(q.getClosePrice()).isEqualTo(new BigDecimal("188.0")))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query MacroYieldSnapshot and latest snapshot")
    void shouldQueryMacroYieldSnapshot() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot snap = new MacroYieldSnapshot(id, now, new BigDecimal("5.25"),
                new BigDecimal("4.20"), new BigDecimal("4.50"), new BigDecimal("-0.10"));

        when(macroYieldRepository.findAll()).thenReturn(Flux.just(snap));
        when(macroYieldRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snap));
        when(macroYieldRepository.findById(id)).thenReturn(Mono.just(snap));

        StepVerifier.create(queryService.listMacroYieldSnapshots(null))
                .assertNext(s -> assertThat(s.getUsCorporateBondEffectiveYield()).isEqualTo(new BigDecimal("5.25")))
                .verifyComplete();

        StepVerifier.create(queryService.getLatestMacroYieldSnapshot())
                .assertNext(s -> assertThat(s.getId()).isEqualTo(id))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query DCA ranks, dividends, and corporate actions")
    void shouldQueryDcaDividendsAndSplits() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        DcaPopularityRank dca = new DcaPopularityRank(id, UUID.randomUUID(), "0050", 2026, 8, 1, 1280000);
        DividendAnnouncement div = new DividendAnnouncement(id, UUID.randomUUID(), "0050", now, now.plusDays(30), new BigDecimal("1.5"), TaxTag.DOMESTIC_54C);
        CorporateAction ca = new CorporateAction(id, UUID.randomUUID(), "0050", CorporateActionType.SPLIT, now, 4, 1);

        when(dcaRankRepository.findByRankingYearAndRankingMonthOrderByRankPositionAsc(2026, 8)).thenReturn(Flux.just(dca));
        when(dividendRepository.findByTickerOrderByExDateDesc("0050")).thenReturn(Flux.just(div));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("0050")).thenReturn(Flux.just(ca));

        StepVerifier.create(queryService.getTop20DcaRanks(2026, 8))
                .assertNext(r -> assertThat(r.getRankPosition()).isEqualTo(1))
                .verifyComplete();

        StepVerifier.create(queryService.listDividendAnnouncements(new DividendAnnouncementFilterInput("0050", null, null)))
                .assertNext(d -> assertThat(d.getDividendPerShare()).isEqualTo(new BigDecimal("1.5")))
                .verifyComplete();

        StepVerifier.create(queryService.listCorporateActions(new CorporateActionFilterInput("0050", null)))
                .assertNext(c -> assertThat(c.getSplitToShares()).isEqualTo(4))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should list DataFeedSyncWatermarks")
    void shouldListDataFeedWatermarks() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        DataFeedSyncWatermark wm = new DataFeedSyncWatermark(id, "TWSE_DAILY_QUOTES", now, now, 100, "SUCCESS", null, now);
        when(watermarkRepository.findAll()).thenReturn(Flux.just(wm));

        StepVerifier.create(queryService.listDataFeedWatermarks())
                .assertNext(res -> {
                    assertThat(res.getFeedName()).isEqualTo("TWSE_DAILY_QUOTES");
                    assertThat(res.getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query latest Top 20 DCA ranks when year and month are null")
    void shouldQueryLatestDcaRanksDynamically() {
        UUID id = UUID.randomUUID();
        DcaPopularityRank dca1 = new DcaPopularityRank(id, UUID.randomUUID(), "0050", 2026, 7, 1, 1000);
        DcaPopularityRank dca2 = new DcaPopularityRank(id, UUID.randomUUID(), "0050", 2026, 8, 1, 2000);
        DcaPopularityRank dca3 = new DcaPopularityRank(id, UUID.randomUUID(), "0056", 2026, 8, 2, 1500);

        when(dcaRankRepository.findAll()).thenReturn(Flux.just(dca1, dca2, dca3));

        GlobalAssetMetadata meta50 = new GlobalAssetMetadata();
        meta50.setName("元大台灣50");
        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(meta50));

        GlobalAssetMetadata meta56 = new GlobalAssetMetadata();
        meta56.setName("元大高股息");
        when(metadataRepository.findByTicker("0056")).thenReturn(Mono.just(meta56));

        StepVerifier.create(queryService.getTop20DcaRanks(null, null))
                .assertNext(r -> {
                    assertThat(r.getRankingMonth()).isEqualTo(8);
                    assertThat(r.getRankPosition()).isEqualTo(1);
                    assertThat(r.getName()).isEqualTo("元大台灣50");
                })
                .assertNext(r -> {
                    assertThat(r.getRankingMonth()).isEqualTo(8);
                    assertThat(r.getRankPosition()).isEqualTo(2);
                    assertThat(r.getName()).isEqualTo("元大高股息");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should enrich GlobalAssetScore with metadata, quotes, and returns")
    void shouldEnrichGlobalAssetScoreWithPerformance() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetScore score = new GlobalAssetScore(id, id, "0050", now, CandidateAssetClass.CORE, 1,
                new BigDecimal("95.0"), new BigDecimal("400000000000"));

        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(CandidateAssetClass.CORE, now))
                .thenReturn(Flux.just(score));

        GlobalAssetMetadata meta = new GlobalAssetMetadata();
        meta.setName("元大台灣50");
        meta.setListingDate(now.minusYears(5));
        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(meta));

        MarketDailyQuote qToday = new MarketDailyQuote(id, id, null, "0050", now,
                new BigDecimal("188.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 1000000L, new BigDecimal("188000000"));
        MarketDailyQuote qPrev = new MarketDailyQuote(id, id, null, "0050", now.minusDays(1),
                new BigDecimal("180.0"), new BigDecimal("186.0"), new BigDecimal("179.0"),
                new BigDecimal("180.0"), 1000000L, new BigDecimal("180000000"));
        MarketDailyQuote q1m = new MarketDailyQuote(id, id, null, "0050", now.minusMonths(1),
                new BigDecimal("160.0"), new BigDecimal("165.0"), new BigDecimal("159.0"),
                new BigDecimal("160.0"), 1000000L, new BigDecimal("160000000"));

        when(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq("0050"), any()))
                .thenReturn(Flux.just(qToday, qPrev, q1m));

        DividendAnnouncement div = new DividendAnnouncement(id, id, "0050", now.minusDays(10), now,
                new BigDecimal("2.0"), TaxTag.DOMESTIC_54C);
        when(dividendRepository.findByTickerOrderByExDateDesc("0050"))
                .thenReturn(Flux.just(div));

        StepVerifier.create(queryService.getScoresByAssetClass(CandidateAssetClass.CORE, now.toString()))
                .assertNext(s -> {
                    assertThat(s.getName()).isEqualTo("元大台灣50");
                    assertThat(s.getClosePrice()).isEqualTo(new BigDecimal("188.0"));
                    // diff: 188 - 180 = 8. pct: 8 / 180 = 4.44%
                    assertThat(s.getChangePct()).isEqualTo(new BigDecimal("4.44"));
                    // return1m: (188 - 160 + 2) / 160 = 30 / 160 = 18.75%
                    assertThat(s.getReturn1m()).isEqualTo(new BigDecimal("18.75"));
                    assertThat(s.getDistributionFrequency()).isEqualTo(DistributionFrequency.ANNUAL);
                })
                .verifyComplete();
    }
}
