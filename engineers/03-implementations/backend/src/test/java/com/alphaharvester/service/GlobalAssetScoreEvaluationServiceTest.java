package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.service.GlobalAssetScoreEvaluationService;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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

    private GlobalAssetScoreEvaluationService service;

    @BeforeEach
    void setUp() {
        lenient().when(dcaRankRepository.findAll()).thenReturn(Flux.empty());
        service = new GlobalAssetScoreEvaluationService(metadataRepository, scoreRepository, quoteRepository, dcaRankRepository);
    }

    @Test
    @DisplayName("Should evaluate and qualify Core asset with low expense ratio and high AUM")
    void shouldEvaluateQualifiedCoreAsset() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata coreAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", now.minusYears(8), "臺灣50",
                new BigDecimal("0.0024"), new BigDecimal("185000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(coreAsset, now);

        assertThat(score.getTicker()).isEqualTo("006208");
        assertThat(score.getIsQualified()).isTrue();
        assertThat(score.getDisqualificationReason()).isNull();
        assertThat(score.getCompositeScore().doubleValue()).isGreaterThan(70.0);
    }

    @Test
    @DisplayName("Should disqualify Core asset when total expense ratio exceeds 0.45%")
    void shouldDisqualifyCoreAssetOnHighExpenseRatio() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata expensiveCore = new GlobalAssetMetadata(
                UUID.randomUUID(), "00999", "昂貴大盤ETF", now.minusYears(3), "某大盤指數",
                new BigDecimal("0.0065"), new BigDecimal("20000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(expensiveCore, now);

        assertThat(score.getIsQualified()).isFalse();
        assertThat(score.getDisqualificationReason()).contains("超過核心大盤上限 0.45%");
    }

    @Test
    @DisplayName("Should disqualify asset if listing days < 30")
    void shouldDisqualifyAssetWithShortListingDays() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata youngAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00998", "剛掛牌ETF", now.minusDays(10), "某指數",
                new BigDecimal("0.0030"), new BigDecimal("15000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(youngAsset, now);

        assertThat(score.getIsQualified()).isFalse();
        assertThat(score.getDisqualificationReason()).contains("未滿 30 個交易日");
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
                null, null, CandidateAssetClass.SATELLITE, null, 1, now, now, null
        );
        GlobalAssetMetadata ndx = new GlobalAssetMetadata(
                UUID.randomUUID(), "00662", "富邦NASDAQ", now, "那斯達克100",
                null, null, CandidateAssetClass.SATELLITE, null, 1, now, now, null
        );
        GlobalAssetMetadata twii = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now, "臺灣50指數",
                null, null, CandidateAssetClass.SATELLITE, null, 1, now, now, null
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
                new BigDecimal("0.0043"), new BigDecimal("420000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        // 006208: starts as SATELLITE, will achieve R^2 >= 0.95 and promote to CORE
        GlobalAssetMetadata asset2 = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", now.minusYears(10), "臺灣50",
                new BigDecimal("0.0024"), new BigDecimal("185000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );

        // 00757: FANG+ satellite, stays SATELLITE (uncorrelated or low R^2 with TWII)
        GlobalAssetMetadata asset3 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00757", "統一FANG+", now.minusYears(5), "FANG+",
                new BigDecimal("0.0060"), new BigDecimal("35000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        // 00679B: bond, starts and stays DEFENSIVE
        GlobalAssetMetadata asset4 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00679B", "元大海美債20年", now.minusYears(7), "彭博20年期以上美國公債指數",
                new BigDecimal("0.0014"), new BigDecimal("250000000000"),
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
            lowCorrQuotes.add(new MarketDailyQuote(null, null, null, "00757", d, null, null, null, BigDecimal.valueOf(p00757), null, null, null, null));

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
                new BigDecimal("0.0035"), new BigDecimal("35000000000"),
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
                new BigDecimal("0.0028"), new BigDecimal("300000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.QUARTERLY, 1, now, now, null
        );

        List<MarketDailyQuote> upQuotes = List.of(
                new MarketDailyQuote(null, null, null, "00878", now, null, null, null, new BigDecimal("25.0"), null, null, null, null),
                new MarketDailyQuote(null, null, null, "00878", now.minusDays(30), null, null, null, new BigDecimal("20.0"), null, null, null, null)
        ); // +25% return

        List<MarketDailyQuote> downQuotes = List.of(
                new MarketDailyQuote(null, null, null, "00878", now, null, null, null, new BigDecimal("18.0"), null, null, null, null),
                new MarketDailyQuote(null, null, null, "00878", now.minusDays(30), null, null, null, new BigDecimal("20.0"), null, null, null, null)
        ); // -10% return

        GlobalAssetScore scoreRank1 = service.evaluateAsset(asset, now, upQuotes, 0.5, 1);
        GlobalAssetScore scoreRank20 = service.evaluateAsset(asset, now, upQuotes, 0.5, 20);
        GlobalAssetScore scoreUnranked = service.evaluateAsset(asset, now, upQuotes, 0.5, null);
        GlobalAssetScore scoreDown = service.evaluateAsset(asset, now, downQuotes, 0.5, 1);

        assertThat(scoreRank1.getCompositeScore()).isGreaterThan(scoreRank20.getCompositeScore());
        assertThat(scoreRank20.getCompositeScore()).isGreaterThan(scoreUnranked.getCompositeScore());
        assertThat(scoreRank1.getCompositeScore()).isGreaterThan(scoreDown.getCompositeScore());
    }
}
