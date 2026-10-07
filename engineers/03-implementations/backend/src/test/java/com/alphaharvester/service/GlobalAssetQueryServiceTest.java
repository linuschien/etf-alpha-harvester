package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.*;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.application.service.MonthlyQuoteCacheService;
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
import static org.mockito.Mockito.*;

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
    @Mock private MonthlyQuoteCacheService quoteCacheService;

    private GlobalAssetQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new GlobalAssetQueryService(
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, scoreRepository, dcaRankRepository,
                dividendRepository, corporateActionRepository, pairwiseMatrixRepository,
                watermarkRepository, quoteCacheService
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

        when(quoteCacheService.getQuoteTimeSeries(eq("0050"), any(), any()))
                .thenReturn(Flux.just(quote));

        StepVerifier.create(queryService.listMarketDailyQuotes(null))
                .assertNext(q -> assertThat(q.getTicker()).isEqualTo("0050"))
                .verifyComplete();

        StepVerifier.create(queryService.getQuoteTimeSeries("0050", "2026-09-01", "2026-09-23"))
                .assertNext(q -> assertThat(q.getClosePrice()).isEqualTo(new BigDecimal("188.0")))
                .verifyComplete();

        StepVerifier.create(queryService.listMarketDailyQuotes(new MarketDailyQuoteFilterInput("0050", "2026-09-01", "2026-09-23")))
                .assertNext(q -> assertThat(q.getTicker()).isEqualTo("0050"))
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

    @Test
    @DisplayName("Should enrich GlobalAssetScore with split-adjusted returns for 0052 (7-to-1 split)")
    void shouldEnrichGlobalAssetScoreWithSplitAdjustedReturns() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetScore score = new GlobalAssetScore(id, id, "0052", now, CandidateAssetClass.SATELLITE, 1,
                new BigDecimal("98.0"), new BigDecimal("30000000000"));

        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(CandidateAssetClass.SATELLITE, now))
                .thenReturn(Flux.just(score));

        GlobalAssetMetadata meta = new GlobalAssetMetadata();
        meta.setName("富邦科技");
        meta.setListingDate(now.minusYears(5));
        when(metadataRepository.findByTicker("0052")).thenReturn(Mono.just(meta));

        // 7:1 split effective 2 months ago
        CorporateAction split = new CorporateAction(id, id, "0052", CorporateActionType.SPLIT, now.minusMonths(2), 7, 1);
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("0052"))
                .thenReturn(Flux.just(split));

        // Quotes: today (post-split 61.50), prev (post-split 60.00), 1m ago (post-split 55.00), 1y ago (pre-split 190.00)
        MarketDailyQuote qToday = new MarketDailyQuote(id, id, null, "0052", now,
                new BigDecimal("60.0"), new BigDecimal("62.0"), new BigDecimal("59.5"),
                new BigDecimal("61.50"), 2000000L, new BigDecimal("123000000"));
        MarketDailyQuote qPrev = new MarketDailyQuote(id, id, null, "0052", now.minusDays(1),
                new BigDecimal("59.0"), new BigDecimal("60.5"), new BigDecimal("58.5"),
                new BigDecimal("60.00"), 2000000L, new BigDecimal("120000000"));
        MarketDailyQuote q1m = new MarketDailyQuote(id, id, null, "0052", now.minusMonths(1),
                new BigDecimal("54.0"), new BigDecimal("56.0"), new BigDecimal("53.5"),
                new BigDecimal("55.00"), 2000000L, new BigDecimal("110000000"));
        MarketDailyQuote q1y = new MarketDailyQuote(id, id, null, "0052", now.minusYears(1),
                new BigDecimal("185.0"), new BigDecimal("192.0"), new BigDecimal("184.0"),
                new BigDecimal("190.00"), 500000L, new BigDecimal("95000000"));

        when(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq("0052"), any()))
                .thenReturn(Flux.just(qToday, qPrev, q1m, q1y));

        // Pre-split dividend: 7.00 per pre-split share (ex-date 5 months ago, before split)
        DividendAnnouncement divPre = new DividendAnnouncement(id, id, "0052", now.minusMonths(5), now,
                new BigDecimal("7.00"), TaxTag.DOMESTIC_54C);
        when(dividendRepository.findByTickerOrderByExDateDesc("0052"))
                .thenReturn(Flux.just(divPre));

        StepVerifier.create(queryService.getScoresByAssetClass(CandidateAssetClass.SATELLITE, now.toString()))
                .assertNext(s -> {
                    assertThat(s.getName()).isEqualTo("富邦科技");
                    assertThat(s.getClosePrice()).isEqualTo(new BigDecimal("61.50"));
                    // Daily change: (61.50 - 60.00) / 60.00 = +2.50%
                    assertThat(s.getChangePct()).isEqualTo(new BigDecimal("2.50"));
                    // 1m return (both post-split, no div): (61.50 - 55.00) / 55.00 = +11.82%
                    assertThat(s.getReturn1m()).isEqualTo(new BigDecimal("11.82"));
                    // 1y return:
                    // q1y adjusted close: 190.00 * (1/7) = 27.1429
                    // div adjusted: 7.00 * (1/7) = 1.0000
                    // gain: 61.50 - 27.1429 + 1.0000 = 35.3571
                    // return1y: 35.3571 / 27.1429 = +130.26% (positive, not -70.9%!)
                    assertThat(s.getReturn1y()).isEqualTo(new BigDecimal("130.26"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should group candidates into clusters using Greedy Leader-Follower Star Topology")
    void shouldClusterCandidatesUsingStarTopology() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetScore s1 = new GlobalAssetScore(id1, id1, "00935", now, CandidateAssetClass.SATELLITE, 1,
                new BigDecimal("92.0"), new BigDecimal("10000000000"));
        GlobalAssetScore s2 = new GlobalAssetScore(id2, id2, "00927", now, CandidateAssetClass.SATELLITE, 2,
                new BigDecimal("88.0"), new BigDecimal("8000000000"));
        GlobalAssetScore s3 = new GlobalAssetScore(id3, id3, "00934", now, CandidateAssetClass.SATELLITE, 3,
                new BigDecimal("85.0"), new BigDecimal("7000000000"));

        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(eq(CandidateAssetClass.SATELLITE), any()))
                .thenReturn(Flux.just(s1, s2, s3));

        GlobalAssetMetadata m1 = new GlobalAssetMetadata();
        m1.setName("野村臺灣新科技50");
        m1.setUnderlyingIndex("特選臺灣新科技50指數");
        when(metadataRepository.findByTicker("00935")).thenReturn(Mono.just(m1));

        GlobalAssetMetadata m2 = new GlobalAssetMetadata();
        m2.setName("群益半導體收益");
        m2.setUnderlyingIndex("半導體收益指數");
        when(metadataRepository.findByTicker("00927")).thenReturn(Mono.just(m2));

        GlobalAssetMetadata m3 = new GlobalAssetMetadata();
        m3.setName("中信成長高股息");
        m3.setUnderlyingIndex("特選臺灣成長高股息指數");
        when(metadataRepository.findByTicker("00934")).thenReturn(Mono.just(m3));

        // Pairwise matrix: 00927 <-> 00935 is 0.88 (>= 0.80), 00934 <-> 00935 is 0.45 (< 0.80), 00927 <-> 00934 is 0.82
        GlobalAssetPairwiseMatrix m21 = new GlobalAssetPairwiseMatrix(UUID.randomUUID(), now, CandidateAssetClass.SATELLITE, "00927", "00935", new BigDecimal("0.88"), new BigDecimal("0.94"), now);
        GlobalAssetPairwiseMatrix m31 = new GlobalAssetPairwiseMatrix(UUID.randomUUID(), now, CandidateAssetClass.SATELLITE, "00934", "00935", new BigDecimal("0.45"), new BigDecimal("0.67"), now);
        GlobalAssetPairwiseMatrix m23 = new GlobalAssetPairwiseMatrix(UUID.randomUUID(), now, CandidateAssetClass.SATELLITE, "00927", "00934", new BigDecimal("0.82"), new BigDecimal("0.91"), now);

        when(pairwiseMatrixRepository.findByEvaluationDateAndAssetClass(any(), eq(CandidateAssetClass.SATELLITE)))
                .thenReturn(Flux.just(m21, m31, m23));

        StepVerifier.create(queryService.getClusteredCandidates(CandidateAssetClass.SATELLITE, null, now.toString()))
                .assertNext(c1 -> {
                    assertThat(c1.clusterId()).isEqualTo(1);
                    assertThat(c1.leader().getTicker()).isEqualTo("00935");
                    assertThat(c1.leader().getUnderlyingIndex()).isEqualTo("特選臺灣新科技50指數");
                    assertThat(c1.isSingleton()).isFalse();
                    assertThat(c1.alternatives()).hasSize(1);
                    assertThat(c1.alternatives().get(0).score().getTicker()).isEqualTo("00927");
                    assertThat(c1.alternatives().get(0).score().getUnderlyingIndex()).isEqualTo("半導體收益指數");
                    assertThat(c1.alternatives().get(0).rSquared()).isEqualTo(0.88);
                })
                .assertNext(c2 -> {
                    assertThat(c2.clusterId()).isEqualTo(2);
                    assertThat(c2.leader().getTicker()).isEqualTo("00934");
                    assertThat(c2.leader().getUnderlyingIndex()).isEqualTo("特選臺灣成長高股息指數");
                    assertThat(c2.isSingleton()).isTrue();
                    assertThat(c2.alternatives()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should respect custom threshold when clustering candidates")
    void shouldRespectCustomThresholdWhenClustering() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetScore s1 = new GlobalAssetScore(id1, id1, "00935", now, CandidateAssetClass.SATELLITE, 1,
                new BigDecimal("92.0"), new BigDecimal("10000000000"));
        GlobalAssetScore s2 = new GlobalAssetScore(id2, id2, "00927", now, CandidateAssetClass.SATELLITE, 2,
                new BigDecimal("88.0"), new BigDecimal("8000000000"));

        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(eq(CandidateAssetClass.SATELLITE), any()))
                .thenReturn(Flux.just(s1, s2));

        GlobalAssetPairwiseMatrix m21 = new GlobalAssetPairwiseMatrix(UUID.randomUUID(), now, CandidateAssetClass.SATELLITE, "00927", "00935", new BigDecimal("0.88"), new BigDecimal("0.94"), now);
        when(pairwiseMatrixRepository.findByEvaluationDateAndAssetClass(any(), eq(CandidateAssetClass.SATELLITE)))
                .thenReturn(Flux.just(m21));

        // Threshold = 0.90 -> 0.88 < 0.90 -> s2 is not absorbed by s1, so both are singletons
        StepVerifier.create(queryService.getClusteredCandidates(CandidateAssetClass.SATELLITE, 0.90, now.toString()))
                .assertNext(c1 -> {
                    assertThat(c1.clusterId()).isEqualTo(1);
                    assertThat(c1.leader().getTicker()).isEqualTo("00935");
                    assertThat(c1.isSingleton()).isTrue();
                    assertThat(c1.alternatives()).isEmpty();
                })
                .assertNext(c2 -> {
                    assertThat(c2.clusterId()).isEqualTo(2);
                    assertThat(c2.leader().getTicker()).isEqualTo("00927");
                    assertThat(c2.isSingleton()).isTrue();
                    assertThat(c2.alternatives()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty Flux when no scores found for clustering")
    void shouldReturnEmptyFluxWhenNoScoresForClustering() {
        LocalDateTime now = LocalDateTime.now();
        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(eq(CandidateAssetClass.SATELLITE), any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(queryService.getClusteredCandidates(CandidateAssetClass.SATELLITE, 0.80, now.toString()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fallback to singletons when pairwise matrix repository is null")
    void shouldFallbackToSingletonsWhenPairwiseRepositoryIsNull() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetScore s = new GlobalAssetScore(id, id, "0050", now, CandidateAssetClass.CORE, 1,
                new BigDecimal("95.0"), new BigDecimal("400000000000"));

        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(eq(CandidateAssetClass.CORE), any()))
                .thenReturn(Flux.just(s));

        GlobalAssetQueryService svcWithoutMatrix = new GlobalAssetQueryService(
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, scoreRepository, dcaRankRepository,
                dividendRepository, corporateActionRepository, null,
                watermarkRepository, quoteCacheService
        );

        StepVerifier.create(svcWithoutMatrix.getClusteredCandidates(CandidateAssetClass.CORE, 0.80, now.toString()))
                .assertNext(c -> {
                    assertThat(c.clusterId()).isEqualTo(1);
                    assertThat(c.leader().getTicker()).isEqualTo("0050");
                    assertThat(c.isSingleton()).isTrue();
                    assertThat(c.alternatives()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should test getOrthogonalCandidates for Shannon Mode")
    void shouldTestGetOrthogonalCandidates() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetScore s1 = new GlobalAssetScore(id1, id1, "0050", now, CandidateAssetClass.CORE, 1,
                new BigDecimal("95.0"), new BigDecimal("400000000000"));
        GlobalAssetScore s2 = new GlobalAssetScore(id2, id2, "006208", now, CandidateAssetClass.CORE, 2,
                new BigDecimal("92.0"), new BigDecimal("100000000000"));

        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(eq(CandidateAssetClass.CORE), any()))
                .thenReturn(Flux.just(s1, s2));

        GlobalAssetPairwiseMatrix m = new GlobalAssetPairwiseMatrix(UUID.randomUUID(), now, CandidateAssetClass.CORE, "0050", "006208", new BigDecimal("0.98"), new BigDecimal("0.99"), now);
        when(pairwiseMatrixRepository.findByEvaluationDateAndAssetClass(any(), eq(CandidateAssetClass.CORE)))
                .thenReturn(Flux.just(m));

        StepVerifier.create(queryService.getOrthogonalCandidates(CandidateAssetClass.CORE, null, now.toString()))
                .assertNext(res1 -> {
                    assertThat(res1.getTicker()).isEqualTo("0050");
                    assertThat(res1.getOrthogonalStatus().name()).isEqualTo("ACCEPTED");
                })
                .assertNext(res2 -> {
                    assertThat(res2.getTicker()).isEqualTo("006208");
                    assertThat(res2.getOrthogonalStatus().name()).isEqualTo("REJECTED_COLLINEAR");
                })
                .verifyComplete();
    }
}
