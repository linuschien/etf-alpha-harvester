package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.DividendAnnouncementRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.service.GlobalAssetScoreEvaluationService;
import com.alphaharvester.domain.entity.DividendAnnouncement;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.TaxTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalAssetScoreEvaluationServiceTest {

    @Mock
    private GlobalAssetMetadataRepository metadataRepository;

    @Mock
    private GlobalAssetScoreRepository scoreRepository;

    @Mock
    private MarketDailyQuoteRepository quoteRepository;

    @Mock
    private DcaPopularityRankRepository dcaRankRepository;

    @Mock
    private DividendAnnouncementRepository dividendRepository;

    private GlobalAssetScoreEvaluationService service;

    @BeforeEach
    void setUp() {
        lenient().when(dcaRankRepository.findAll()).thenReturn(Flux.empty());
        lenient().when(dividendRepository.findByExDateBetweenOrderByExDateAsc(any(), any())).thenReturn(Flux.empty());
        service = new GlobalAssetScoreEvaluationService(metadataRepository, scoreRepository, quoteRepository, dcaRankRepository, dividendRepository);
    }

    @Test
    @DisplayName("Should evaluate and qualify Core asset with low expense ratio and high AUM")
    void shouldEvaluateQualifiedCoreAsset() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata coreAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", now.minusYears(8), "臺灣50",
                new BigDecimal("185000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(coreAsset, now);

        assertThat(score).isNotNull();
        assertThat(score.getTicker()).isEqualTo("006208");
        assertThat(score.getCompositeScore().doubleValue()).isGreaterThan(70.0);
    }

    @Test
    @DisplayName("Should award higher score to Core asset with higher DCA rank")
    void shouldAwardHigherScoreToCoreAssetWithHigherDcaRank() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata coreAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now.minusYears(20), "臺灣50",
                new BigDecimal("400000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        GlobalAssetScore scoreRank1 = service.evaluateAsset(coreAsset, now, null, 0.98, 1);
        GlobalAssetScore scoreRank20 = service.evaluateAsset(coreAsset, now, null, 0.98, 20);
        GlobalAssetScore scoreUnranked = service.evaluateAsset(coreAsset, now, null, 0.98, null);

        assertThat(scoreRank1.getCompositeScore()).isGreaterThan(scoreRank20.getCompositeScore());
        assertThat(scoreRank20.getCompositeScore()).isGreaterThan(scoreUnranked.getCompositeScore());
    }

    @Test
    @DisplayName("Should qualify Core asset meeting AUM threshold without TER constraint")
    void shouldQualifyCoreAssetWithoutTerConstraint() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata core = new GlobalAssetMetadata(
                UUID.randomUUID(), "00999", "大盤ETF", now.minusYears(3), "某大盤指數",
                new BigDecimal("20000000000"), // 20B >= 10B
                CandidateAssetClass.CORE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(core, now);

        assertThat(score).isNotNull();
    }

    @Test
    @DisplayName("Should disqualify Core asset when AUM is below 10B TWD")
    void shouldDisqualifyCoreAssetOnLowAum() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata smallAumCore = new GlobalAssetMetadata(
                UUID.randomUUID(), "00998", "小規模大盤ETF", now.minusMonths(3), "某指數",
                new BigDecimal("4000000000"), // 4B < 10B
                CandidateAssetClass.CORE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(smallAumCore, now);

        assertThat(score).isNull();
    }

    @Test
    @DisplayName("Should calculate perfect R^2 = 1.0 when ETF returns match benchmark")
    void shouldCalculatePerfectR2() {
        LocalDateTime now = LocalDateTime.now();
        List<MarketDailyQuote> bmQuotes = new ArrayList<>();
        List<MarketDailyQuote> etfQuotes = new ArrayList<>();

        double bmPrice = 20000.0;
        double etfPrice = 100.0;
        for (int i = 0; i < 20; i++) {
            LocalDateTime d = now.minusDays(20 - i);
            bmQuotes.add(new MarketDailyQuote(
                    null, null, null, "^TWII", d,
                    null, null, null, BigDecimal.valueOf(bmPrice), null, null, null, null
            ));
            etfQuotes.add(new MarketDailyQuote(
                    null, null, null, "0050", d,
                    null, null, null, BigDecimal.valueOf(etfPrice), null, null, null, null
            ));
            // Simulate daily return: alternating +1% and -0.5%
            double factor = (i % 2 == 0) ? 1.01 : 0.995;
            bmPrice *= factor;
            etfPrice *= factor;
        }

        double r2 = service.calculateR2(etfQuotes, bmQuotes);
        assertThat(r2).isGreaterThanOrEqualTo(0.999);
    }

    @Test
    @DisplayName("Should return R^2 = 0.0 for inverse or negatively correlated ETF")
    void shouldReturnZeroR2ForInverseCorrelation() {
        LocalDateTime now = LocalDateTime.now();
        List<MarketDailyQuote> bmQuotes = new ArrayList<>();
        List<MarketDailyQuote> etfQuotes = new ArrayList<>();

        double bmPrice = 20000.0;
        double etfPrice = 100.0;
        for (int i = 0; i < 20; i++) {
            LocalDateTime d = now.minusDays(20 - i);
            bmQuotes.add(new MarketDailyQuote(
                    null, null, null, "^TWII", d,
                    null, null, null, BigDecimal.valueOf(bmPrice), null, null, null, null
            ));
            etfQuotes.add(new MarketDailyQuote(
                    null, null, null, "00632R", d,
                    null, null, null, BigDecimal.valueOf(etfPrice), null, null, null, null
            ));
            // Benchmark goes up 1%, ETF goes down 1%
            double bmFactor = (i % 2 == 0) ? 1.01 : 0.99;
            double etfFactor = (i % 2 == 0) ? 0.99 : 1.01;
            bmPrice *= bmFactor;
            etfPrice *= etfFactor;
        }

        double r2 = service.calculateR2(etfQuotes, bmQuotes);
        assertThat(r2).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should return 0.0 for insufficient quote data (< 3 dates)")
    void shouldReturnZeroForInsufficientData() {
        double r2 = service.calculateR2(List.of(), List.of());
        assertThat(r2).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should correctly resolve benchmark ticker based on asset metadata")
    void shouldResolveBenchmarkTicker() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata sp500 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00646", "元大S&P500", now, "標普500指數",
                null, CandidateAssetClass.SATELLITE, null, 1, now, now, null
        );
        GlobalAssetMetadata ndx = new GlobalAssetMetadata(
                UUID.randomUUID(), "00662", "富邦NASDAQ", now, "那斯達克100",
                null, CandidateAssetClass.SATELLITE, null, 1, now, now, null
        );
        GlobalAssetMetadata twii = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now, "臺灣50指數",
                null, CandidateAssetClass.SATELLITE, null, 1, now, now, null
        );

        assertThat(service.resolveBenchmarkTicker(sp500)).isEqualTo("^GSPC");
        assertThat(service.resolveBenchmarkTicker(ndx)).isEqualTo("^NDX");
        assertThat(service.resolveBenchmarkTicker(twii)).isEqualTo("^TWII");
    }

    @Test
    @DisplayName("Should execute evaluateGlobalAssetScores pipeline with dynamic R^2 classification")
    void shouldExecuteEvaluationPipelineWithDynamicClassification() {
        LocalDateTime now = LocalDateTime.now();

        // 0050: starts as SATELLITE, will achieve R^2 >= 0.95 and promote to CORE
        GlobalAssetMetadata asset1 = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now.minusYears(15), "臺灣50",
                new BigDecimal("420000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        // 006208: starts as SATELLITE, will achieve R^2 >= 0.95 and promote to CORE
        GlobalAssetMetadata asset2 = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", now.minusYears(10), "臺灣50",
                new BigDecimal("185000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        // 00757: FANG+ satellite, stays SATELLITE (uncorrelated or low R^2 with TWII)
        GlobalAssetMetadata asset3 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00757", "統一FANG+", now.minusYears(5), "FANG+",
                new BigDecimal("35000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        // 00679B: bond, starts and stays DEFENSIVE
        GlobalAssetMetadata asset4 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00679B", "元大海美債20年", now.minusYears(7), "彭博20年期以上美國公債指數",
                new BigDecimal("250000000000"),
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.QUARTERLY, 1, now, now, null
        );

        // Mock benchmark quotes (^TWII)
        List<MarketDailyQuote> twiiQuotes = new ArrayList<>();
        List<MarketDailyQuote> highCorrQuotes1 = new ArrayList<>();
        List<MarketDailyQuote> highCorrQuotes2 = new ArrayList<>();
        List<MarketDailyQuote> lowCorrQuotes = new ArrayList<>();

        double pTwii = 20000.0;
        double p0050 = 180.0;
        double p006208 = 100.0;
        double p00757 = 80.0;

        for (int i = 0; i < 20; i++) {
            LocalDateTime d = now.minusDays(20 - i);
            twiiQuotes.add(new MarketDailyQuote(null, null, null, "^TWII", d, null, null, null, BigDecimal.valueOf(pTwii), null, null, null, null));
            highCorrQuotes1.add(new MarketDailyQuote(null, null, null, "0050", d, null, null, null, BigDecimal.valueOf(p0050), null, null, null, null));
            highCorrQuotes2.add(new MarketDailyQuote(null, null, null, "006208", d, null, null, null, BigDecimal.valueOf(p006208), null, null, null, null));
            lowCorrQuotes.add(new MarketDailyQuote(null, null, null, "00757", d, null, null, null, BigDecimal.valueOf(p00757), 1_000_000L, BigDecimal.valueOf(50_000_000), null, null));

            double factor = (i % 2 == 0) ? 1.01 : 0.995;
            pTwii *= factor;
            p0050 *= factor;
            p006208 *= factor;
            p00757 *= (i % 3 == 0) ? 1.02 : 0.98; // Different movement pattern
        }

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset1, asset2, asset3, asset4));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^TWII")).thenReturn(Flux.fromIterable(twiiQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^GSPC")).thenReturn(Flux.empty());
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^NDX")).thenReturn(Flux.empty());

        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.fromIterable(highCorrQuotes1));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("006208")).thenReturn(Flux.fromIterable(highCorrQuotes2));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00757")).thenReturn(Flux.fromIterable(lowCorrQuotes));
        List<MarketDailyQuote> bondQuotes = List.of(
                new MarketDailyQuote(null, null, null, "00679B", now, null, null, null, BigDecimal.valueOf(30.0), 10_000_000L, BigDecimal.valueOf(300_000_000), null, null)
        );
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00679B")).thenReturn(Flux.fromIterable(bondQuotes));

        when(metadataRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));
        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        StepVerifier.create(service.evaluateGlobalAssetScores())
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.evaluatedCandidatesCount()).isEqualTo(4);
                    assertThat(res.coreCount()).isEqualTo(2); // 0050, 006208 dynamically promoted to CORE
                    assertThat(res.satelliteCount()).isEqualTo(1); // 00757 remains SATELLITE
                    assertThat(res.defensiveCount()).isEqualTo(1); // 00679B remains DEFENSIVE
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should promote asset matching S&P500 benchmark to CORE")
    void shouldPromoteAssetMatchingSP500ToCore() {
        LocalDateTime now = LocalDateTime.now();

        // 00646 tracking S&P500
        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00646", "元大S&P500", now.minusYears(8), "標普500",
                new BigDecimal("35000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        List<MarketDailyQuote> gspcQuotes = new ArrayList<>();
        List<MarketDailyQuote> etfQuotes = new ArrayList<>();
        double pGspc = 5000.0;
        double pEtf = 50.0;
        for (int i = 0; i < 20; i++) {
            LocalDateTime d = now.minusDays(20 - i);
            gspcQuotes.add(new MarketDailyQuote(null, null, null, "^GSPC", d, null, null, null, BigDecimal.valueOf(pGspc), null, null, null, null));
            etfQuotes.add(new MarketDailyQuote(null, null, null, "00646", d, null, null, null, BigDecimal.valueOf(pEtf), null, null, null, null));
            double factor = (i % 2 == 0) ? 1.01 : 0.995;
            pGspc *= factor;
            pEtf *= factor;
        }

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^TWII")).thenReturn(Flux.empty());
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^GSPC")).thenReturn(Flux.fromIterable(gspcQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^NDX")).thenReturn(Flux.empty());
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00646")).thenReturn(Flux.fromIterable(etfQuotes));

        when(metadataRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));
        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        StepVerifier.create(service.evaluateGlobalAssetScores())
                .assertNext(res -> {
                    assertThat(res.coreCount()).isEqualTo(1); // Promoted to CORE via ^GSPC match
                    assertThat(res.satelliteCount()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should evaluate linear scores for DCA rank and momentum")
    void shouldEvaluateLinearScoresForDcaRankAndMom() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00878", "國泰永續高股息", now.minusYears(4), "MSCI臺灣ESG",
                new BigDecimal("300000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.QUARTERLY, 1, now, now, null
        );

        List<MarketDailyQuote> upQuotes = List.of(
                new MarketDailyQuote(null, null, null, "00878", now, null, null, null, new BigDecimal("25.0"), 1_000_000L, new BigDecimal("25000000"), null, null),
                new MarketDailyQuote(null, null, null, "00878", now.minusDays(30), null, null, null, new BigDecimal("20.0"), 1_000_000L, new BigDecimal("20000000"), null, null)
        ); // +25% return

        List<MarketDailyQuote> downQuotes = List.of(
                new MarketDailyQuote(null, null, null, "00878", now, null, null, null, new BigDecimal("18.0"), 1_000_000L, new BigDecimal("25000000"), null, null),
                new MarketDailyQuote(null, null, null, "00878", now.minusDays(30), null, null, null, new BigDecimal("20.0"), 1_000_000L, new BigDecimal("20000000"), null, null)
        ); // -10% return

        GlobalAssetScore scoreRank1 = service.evaluateAsset(asset, now, upQuotes, 0.5, 1);
        GlobalAssetScore scoreRank20 = service.evaluateAsset(asset, now, upQuotes, 0.5, 20);
        GlobalAssetScore scoreUnranked = service.evaluateAsset(asset, now, upQuotes, 0.5, null);
        GlobalAssetScore scoreDown = service.evaluateAsset(asset, now, downQuotes, 0.5, 1);

        assertThat(scoreRank1.getCompositeScore()).isGreaterThan(scoreRank20.getCompositeScore());
        assertThat(scoreRank20.getCompositeScore()).isGreaterThan(scoreUnranked.getCompositeScore());
        assertThat(scoreRank1.getCompositeScore()).isGreaterThan(scoreDown.getCompositeScore());
    }

    @Test
    @DisplayName("Should award higher score to Satellite asset with lower correlation to Core index")
    void shouldAwardHigherScoreToUncorrelatedSatelliteAsset() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00757", "統一FANG+", now.minusYears(5), "FANG+",
                new BigDecimal("35000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        List<MarketDailyQuote> quotes = List.of(
                new MarketDailyQuote(null, null, null, "00757", now, null, null, null,
                        new BigDecimal("80.0"), 1_000_000L, new BigDecimal("80000000"), null, null),
                new MarketDailyQuote(null, null, null, "00757", now.minusDays(1), null, null, null,
                        new BigDecimal("78.0"), 1_000_000L, new BigDecimal("78000000"), null, null)
        );

        // Low correlation R^2 = 0.16 (rho = 0.40) vs High correlation R^2 = 0.81 (rho = 0.90)
        GlobalAssetScore lowCorrScore = service.evaluateAsset(asset, now, quotes, 0.16, 5);
        GlobalAssetScore highCorrScore = service.evaluateAsset(asset, now, quotes, 0.81, 5);

        // Lower correlation means higher diversification score (1 - rho)*100
        assertThat(lowCorrScore.getCompositeScore()).isGreaterThan(highCorrScore.getCompositeScore());
    }

    @Test
    @DisplayName("Should disqualify Satellite asset when quotes are missing (null or empty)")
    void shouldDisqualifySatelliteAssetWhenQuotesAreMissing() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00757", "統一FANG+", now.minusYears(5), "FANG+",
                new BigDecimal("35000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore scoreNullQuotes = service.evaluateAsset(asset, now, null, 0.16, 5);
        GlobalAssetScore scoreEmptyQuotes = service.evaluateAsset(asset, now, List.of(), 0.16, 5);

        assertThat(scoreNullQuotes).isNull();
        assertThat(scoreEmptyQuotes).isNull();
    }

    @Test
    @DisplayName("Should disqualify Satellite asset when AUM is below 2B TWD")
    void shouldDisqualifySatelliteAssetOnLowAum() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata smallSatellite = new GlobalAssetMetadata(
                UUID.randomUUID(), "00991", "超微型衛星", now.minusYears(1), "某主題指數",
                new BigDecimal("1500000000"), // 1.5B < 2B
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(smallSatellite, now);

        assertThat(score).isNull();
    }

    @Test
    @DisplayName("Should disqualify Satellite asset when rolling 30d turnover is below 20M TWD")
    void shouldDisqualifySatelliteAssetOnLowTurnover() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata illiquidSatellite = new GlobalAssetMetadata(
                UUID.randomUUID(), "00992", "冷門衛星", now.minusYears(2), "冷門指數",
                new BigDecimal("5000000000"), // 5B > 2B
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        List<MarketDailyQuote> quotes = List.of(
                new MarketDailyQuote(null, null, null, "00992", now, null, null, null,
                        new BigDecimal("20.0"), 500_000L, new BigDecimal("10000000"), null, null), // 1,000萬 < 2,000萬
                new MarketDailyQuote(null, null, null, "00992", now.minusDays(1), null, null, null,
                        new BigDecimal("20.0"), 600_000L, new BigDecimal("12000000"), null, null)  // 1,200萬 < 2,000萬
        );

        GlobalAssetScore score = service.evaluateAsset(illiquidSatellite, now, quotes, 0.20, null);

        assertThat(score).isNull();
    }

    @Test
    @DisplayName("Should disqualify Defensive asset when AUM is below 5B TWD")
    void shouldDisqualifyDefensiveAssetOnLowAum() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata smallBond = new GlobalAssetMetadata(
                UUID.randomUUID(), "00993B", "小微公債", now.minusYears(1), "公債指數",
                new BigDecimal("3000000000"), // 3B < 5B
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.QUARTERLY, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(smallBond, now);

        assertThat(score).isNull();
    }

    @Test
    @DisplayName("Should disqualify Defensive asset on leveraged or inverse ticker")
    void shouldDisqualifyDefensiveAssetOnLeverageOrInverse() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata leveragedBond = new GlobalAssetMetadata(
                UUID.randomUUID(), "00680L", "元大美債20正2", now.minusYears(5), "20年美債正2",
                new BigDecimal("10000000000"), // 10B > 5B
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetMetadata inverseBond = new GlobalAssetMetadata(
                UUID.randomUUID(), "00681R", "元大美債20反1", now.minusYears(5), "20年美債反1",
                new BigDecimal("8000000000"), // 8B > 5B
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore scoreL = service.evaluateAsset(leveragedBond, now);
        GlobalAssetScore scoreR = service.evaluateAsset(inverseBond, now);

        assertThat(scoreL).isNull();
        assertThat(scoreR).isNull();
    }

    @Test
    @DisplayName("Should calculate Defensive yield score from dividend announcement history")
    void shouldCalculateDefensiveYieldFromDividendHistory() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata bondAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00720B", "元大投資級公司債", now.minusYears(6), "投資級公司債",
                new BigDecimal("120000000000"), // 120B > 5B
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.QUARTERLY, 1, now, now, null
        );

        List<MarketDailyQuote> quotes = List.of(
                new MarketDailyQuote(null, null, null, "00720B", now, null, null, null,
                        new BigDecimal("30.0"), 1000000L, new BigDecimal("30000000"), null, null)
        );

        // 4 quarters * 0.45 = 1.8 TWD / share. Annual yield = 1.8 / 30.0 = 6% -> yieldScore = 100.0
        List<DividendAnnouncement> dividends = List.of(
                new DividendAnnouncement(null, null, "00720B", now.minusMonths(2), now.minusMonths(1), new BigDecimal("0.45"), TaxTag.OVERSEAS_76W),
                new DividendAnnouncement(null, null, "00720B", now.minusMonths(5), now.minusMonths(4), new BigDecimal("0.45"), TaxTag.OVERSEAS_76W),
                new DividendAnnouncement(null, null, "00720B", now.minusMonths(8), now.minusMonths(7), new BigDecimal("0.45"), TaxTag.OVERSEAS_76W),
                new DividendAnnouncement(null, null, "00720B", now.minusMonths(11), now.minusMonths(10), new BigDecimal("0.45"), TaxTag.OVERSEAS_76W)
        );

        GlobalAssetScore scoreWithDivs = service.evaluateAsset(bondAsset, now, quotes, 0.0, null, dividends);
        GlobalAssetScore scoreNoDivs = service.evaluateAsset(bondAsset, now, quotes, 0.0, null, List.of());

        assertThat(scoreWithDivs).isNotNull();
        assertThat(scoreNoDivs).isNotNull();
        // With 6% yield, yieldScore = 100, which is higher than default 75 when dividends are empty
        assertThat(scoreWithDivs.getCompositeScore()).isGreaterThan(scoreNoDivs.getCompositeScore());
    }

    @Test
    @DisplayName("Should eliminate disqualified assets so they are NOT ranked or saved to scoreRepository")
    @SuppressWarnings("unchecked")
    void shouldFilterDisqualifiedAssetsFromScoringPipelineAndNotPersistThem() {
        LocalDateTime now = LocalDateTime.now();

        // Qualified Core: 0050
        GlobalAssetMetadata qualifiedCore = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now.minusYears(15), "臺灣50",
                new BigDecimal("420000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        // Disqualified Core: low AUM (< 10B)
        GlobalAssetMetadata smallCore = new GlobalAssetMetadata(
                UUID.randomUUID(), "00999", "微型核心", now.minusYears(3), "臺灣50",
                new BigDecimal("5000000000"), // 5B < 10B
                CandidateAssetClass.CORE, DistributionFrequency.NONE, 1, now, now, null
        );

        // Disqualified Satellite: low AUM
        GlobalAssetMetadata smallSatellite = new GlobalAssetMetadata(
                UUID.randomUUID(), "00991", "微型衛星", now.minusYears(2), "主題指數",
                new BigDecimal("1000000000"), // 1B < 2B
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        // Disqualified Defensive: Leveraged ETF
        GlobalAssetMetadata leveragedBond = new GlobalAssetMetadata(
                UUID.randomUUID(), "00680L", "槓桿美債正2", now.minusYears(3), "美債正2",
                new BigDecimal("10000000000"),
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.NONE, 1, now, now, null
        );

        // Mock quotes to achieve R^2 >= 0.95 for 0050 and 00999 against ^TWII
        List<MarketDailyQuote> twiiQuotes = new ArrayList<>();
        List<MarketDailyQuote> corrQuotes1 = new ArrayList<>();
        List<MarketDailyQuote> corrQuotes2 = new ArrayList<>();
        double pTwii = 20000.0;
        double p0050 = 180.0;
        double p00999 = 50.0;
        for (int i = 0; i < 20; i++) {
            LocalDateTime d = now.minusDays(20 - i);
            twiiQuotes.add(new MarketDailyQuote(null, null, null, "^TWII", d, null, null, null, BigDecimal.valueOf(pTwii), null, null, null, null));
            corrQuotes1.add(new MarketDailyQuote(null, null, null, "0050", d, null, null, null, BigDecimal.valueOf(p0050), null, null, null, null));
            corrQuotes2.add(new MarketDailyQuote(null, null, null, "00999", d, null, null, null, BigDecimal.valueOf(p00999), null, null, null, null));
            double factor = (i % 2 == 0) ? 1.01 : 0.995;
            pTwii *= factor;
            p0050 *= factor;
            p00999 *= factor;
        }

        when(metadataRepository.findAll()).thenReturn(Flux.just(qualifiedCore, smallCore, smallSatellite, leveragedBond));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^TWII")).thenReturn(Flux.fromIterable(twiiQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^GSPC")).thenReturn(Flux.empty());
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^NDX")).thenReturn(Flux.empty());

        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.fromIterable(corrQuotes1));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00999")).thenReturn(Flux.fromIterable(corrQuotes2));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00991")).thenReturn(Flux.empty());
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00680L")).thenReturn(Flux.empty());

        lenient().when(metadataRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));
        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        StepVerifier.create(service.evaluateGlobalAssetScores())
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    // Only 1 asset (0050) passed hard constraints
                    assertThat(res.evaluatedCandidatesCount()).isEqualTo(1);
                    assertThat(res.coreCount()).isEqualTo(1);
                    assertThat(res.satelliteCount()).isEqualTo(0);
                    assertThat(res.defensiveCount()).isEqualTo(0);
                })
                .verifyComplete();

        ArgumentCaptor<List<GlobalAssetScore>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoreRepository).saveAll(captor.capture());
        List<GlobalAssetScore> savedScores = captor.getValue();

        // Exactly 1 score saved, and it's 0050
        assertThat(savedScores).hasSize(1);
        assertThat(savedScores.get(0).getTicker()).isEqualTo("0050");
        assertThat(savedScores.get(0).getClassRank()).isEqualTo(1);

        // Disqualified candidates 00999, 00991, 00680L MUST NOT be present in saved scores!
        assertThat(savedScores).noneMatch(s -> "00999".equals(s.getTicker()));
        assertThat(savedScores).noneMatch(s -> "00991".equals(s.getTicker()));
        assertThat(savedScores).noneMatch(s -> "00680L".equals(s.getTicker()));
    }

    @Test
    @DisplayName("Should disqualify Defensive asset when market quotes are null or empty")
    void shouldDisqualifyDefensiveAssetWhenQuotesAreMissing() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata bondAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00679B", "元大美債20年", now.minusYears(7), "彭博20年美債",
                new BigDecimal("250000000000"),
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.QUARTERLY, 1, now, now, null
        );

        GlobalAssetScore scoreNullQuotes = service.evaluateAsset(bondAsset, now, null, 0.0, null);
        GlobalAssetScore scoreEmptyQuotes = service.evaluateAsset(bondAsset, now, List.of(), 0.0, null);

        assertThat(scoreNullQuotes).isNull();
        assertThat(scoreEmptyQuotes).isNull();
    }

    @Test
    @DisplayName("Should dynamically derive distribution frequency from 1-year dividend announcement count")
    void shouldDeriveDistributionFrequencyFromDividendCount() {
        LocalDateTime now = LocalDateTime.now();

        // 12 monthly divs (>= 10)
        List<DividendAnnouncement> monthlyDivs = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            monthlyDivs.add(new DividendAnnouncement(null, null, "00929", now.minusMonths(i), now.minusMonths(i), new BigDecimal("0.18"), TaxTag.DOMESTIC_54C));
        }
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(monthlyDivs)).isEqualTo(DistributionFrequency.MONTHLY);

        // 4 quarterly divs (3 ~ 9)
        List<DividendAnnouncement> quarterlyDivs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            quarterlyDivs.add(new DividendAnnouncement(null, null, "00878", now.minusMonths(i * 3), now.minusMonths(i * 3), new BigDecimal("0.55"), TaxTag.DOMESTIC_54C));
        }
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(quarterlyDivs)).isEqualTo(DistributionFrequency.QUARTERLY);

        // 2 semi-annual divs (2)
        List<DividendAnnouncement> semiAnnualDivs = List.of(
                new DividendAnnouncement(null, null, "0050", now.minusMonths(6), now.minusMonths(6), new BigDecimal("1.0"), TaxTag.DOMESTIC_54C),
                new DividendAnnouncement(null, null, "0050", now.minusMonths(1), now.minusMonths(1), new BigDecimal("3.0"), TaxTag.DOMESTIC_54C)
        );
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(semiAnnualDivs)).isEqualTo(DistributionFrequency.SEMI_ANNUAL);

        // 1 annual div (1)
        List<DividendAnnouncement> annualDiv = List.of(
                new DividendAnnouncement(null, null, "00999", now.minusMonths(3), now.minusMonths(3), new BigDecimal("1.5"), TaxTag.DOMESTIC_54C)
        );
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(annualDiv)).isEqualTo(DistributionFrequency.ANNUAL);

        // 0 divs / null
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(List.of())).isEqualTo(DistributionFrequency.NONE);
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(null)).isEqualTo(DistributionFrequency.NONE);
    }

    @Test
    @DisplayName("Should accurately derive distribution frequency for newly listed ETFs using median interval")
    void shouldDeriveFrequencyForNewlyListedEtfUsingMedianInterval() {
        LocalDateTime now = LocalDateTime.now();

        // Case 1: Newly listed monthly ETF (listed 100 days ago, only 3 dividends so far, 30 days apart)
        LocalDateTime listingDate100d = now.minusDays(100);
        List<DividendAnnouncement> newMonthlyDivs = List.of(
                new DividendAnnouncement(null, null, "00940", now.minusDays(70), now.minusDays(60), new BigDecimal("0.05"), TaxTag.DOMESTIC_54C),
                new DividendAnnouncement(null, null, "00940", now.minusDays(40), now.minusDays(30), new BigDecimal("0.05"), TaxTag.DOMESTIC_54C),
                new DividendAnnouncement(null, null, "00940", now.minusDays(10), now, new BigDecimal("0.05"), TaxTag.DOMESTIC_54C)
        );
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(newMonthlyDivs, listingDate100d, now))
                .isEqualTo(DistributionFrequency.MONTHLY);

        // Case 2: Newly listed quarterly ETF (listed 180 days ago, only 2 dividends so far, 90 days apart)
        LocalDateTime listingDate180d = now.minusDays(180);
        List<DividendAnnouncement> newQuarterlyDivs = List.of(
                new DividendAnnouncement(null, null, "00990", now.minusDays(100), now.minusDays(90), new BigDecimal("0.35"), TaxTag.DOMESTIC_54C),
                new DividendAnnouncement(null, null, "00990", now.minusDays(10), now, new BigDecimal("0.35"), TaxTag.DOMESTIC_54C)
        );
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(newQuarterlyDivs, listingDate180d, now))
                .isEqualTo(DistributionFrequency.QUARTERLY);

        // Case 3: Newly listed ETF with only 1 dividend within 40 days of listing
        LocalDateTime listingDate40d = now.minusDays(40);
        List<DividendAnnouncement> singleDiv40d = List.of(
                new DividendAnnouncement(null, null, "00995", now.minusDays(5), now, new BigDecimal("0.10"), TaxTag.DOMESTIC_54C)
        );
        assertThat(GlobalAssetScoreEvaluationService.deriveDistributionFrequency(singleDiv40d, listingDate40d, now))
                .isEqualTo(DistributionFrequency.MONTHLY);
    }

    @Test
    @DisplayName("Should annualize dividend yield for newly listed Defensive bond ETF (listed < 365 days)")
    void shouldAnnualizeDefensiveYieldForNewlyListedBond() {
        LocalDateTime now = LocalDateTime.now();
        // Bond listed only 90 days ago
        LocalDateTime listingDate = now.minusDays(90);
        GlobalAssetMetadata youngBond = new GlobalAssetMetadata(
                UUID.randomUUID(), "00937B", "群益ESG投等債20+", listingDate, "ESG投等債20+",
                new BigDecimal("200000000000"), // 200B
                CandidateAssetClass.DEFENSIVE, DistributionFrequency.MONTHLY, 1, now, now, null
        );

        List<MarketDailyQuote> quotes = List.of(
                new MarketDailyQuote(null, null, null, "00937B", now, null, null, null,
                        new BigDecimal("15.0"), 5000000L, new BigDecimal("75000000"), null, null)
        );

        // 3 monthly distributions of 0.08 = 0.24 TWD. Raw yield = 0.24 / 15.0 = 1.6%
        // Annualized yield = 1.6% * (365 / 90) = 6.489% -> yieldScore = min(100, 6.489% * 1666.67) = 100.0
        List<DividendAnnouncement> divs = List.of(
                new DividendAnnouncement(null, null, "00937B", now.minusDays(70), now.minusDays(60), new BigDecimal("0.08"), TaxTag.OVERSEAS_76W),
                new DividendAnnouncement(null, null, "00937B", now.minusDays(40), now.minusDays(30), new BigDecimal("0.08"), TaxTag.OVERSEAS_76W),
                new DividendAnnouncement(null, null, "00937B", now.minusDays(10), now, new BigDecimal("0.08"), TaxTag.OVERSEAS_76W)
        );

        GlobalAssetScore score = service.evaluateAsset(youngBond, now, quotes, 0.0, null, divs);

        assertThat(score).isNotNull();
        // S_defensive = 0.60 * 100.0 (yield) + 0.40 * 100.0 (aum) = 60 + 40 = 100.00
        assertThat(score.getCompositeScore()).isGreaterThan(new BigDecimal("90.00"));
    }
}
