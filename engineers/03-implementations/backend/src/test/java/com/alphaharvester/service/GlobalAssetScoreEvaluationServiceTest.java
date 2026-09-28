package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.CorporateActionRepository;
import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.DividendAnnouncementRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.application.service.GlobalAssetScoreEvaluationService;
import com.alphaharvester.domain.entity.CorporateAction;
import com.alphaharvester.domain.entity.DividendAnnouncement;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.CorporateActionType;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

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

    @Mock
    private CorporateActionRepository corporateActionRepository;

    @Mock
    private ExternalMarketDataPort externalMarketDataPort;

    private GlobalAssetScoreEvaluationService service;

    @BeforeEach
    void setUp() {
        lenient().when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Collections.emptyMap()));
        lenient().when(dcaRankRepository.findAll()).thenReturn(Flux.empty());
        lenient().when(dividendRepository.findByExDateBetweenOrderByExDateAsc(any(), any())).thenReturn(Flux.empty());
        lenient().when(corporateActionRepository.findByEffectiveDateBetweenOrderByEffectiveDateAsc(any(), any())).thenReturn(Flux.empty());
        lenient().when(quoteRepository.findByTickerOrderByTradeDateDesc(any())).thenReturn(Flux.empty());
        lenient().when(scoreRepository.deleteByEvaluationDate(any())).thenReturn(Mono.empty());
        service = new GlobalAssetScoreEvaluationService(metadataRepository, scoreRepository, quoteRepository, dcaRankRepository, dividendRepository, null, null, externalMarketDataPort, corporateActionRepository);
    }

    private List<MarketDailyQuote> generateQuotesForWindow(String ticker, double startPrice, double growthRate, double noiseFactor, int pattern) {
        YearMonth ym = YearMonth.from(LocalDate.now());
        LocalDate evalDate = ym.atDay(1);
        LocalDate cutoffDate = evalDate.minusDays(1);
        LocalDate start = evalDate.minusYears(1);

        List<MarketDailyQuote> quotes = new ArrayList<>();
        double p = startPrice;
        LocalDate curr = start;
        int i = 0;
        while (!curr.isAfter(cutoffDate)) {
            if (curr.getDayOfWeek().getValue() <= 5) {
                double factor;
                if (pattern == 0) {
                    double noise = (i % 2 == 0) ? noiseFactor : -noiseFactor;
                    factor = 1.0 + growthRate + noise;
                } else if (pattern == 1) {
                    factor = 1.0 + ((i % 4 < 2) ? 0.025 : -0.018);
                } else if (pattern == 2) {
                    double noise = ((i - 1) % 2 == 0) ? noiseFactor : -noiseFactor;
                    factor = 1.0 + growthRate + noise;
                } else {
                    factor = 1.0 + growthRate;
                }
                p *= factor;
                quotes.add(new MarketDailyQuote(
                        UUID.randomUUID(), UUID.randomUUID(), null, ticker, curr.atTime(13, 30),
                        BigDecimal.valueOf(p), BigDecimal.valueOf(p * 1.01), BigDecimal.valueOf(p * 0.99),
                        BigDecimal.valueOf(p), 1_000_000L, BigDecimal.valueOf(50_000_000L),
                        BigDecimal.valueOf(p), BigDecimal.ZERO
                ));
                i++;
            }
            curr = curr.plusDays(1);
        }
        return quotes;
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
                1, now, now, null
        );
        GlobalAssetMetadata ndx = new GlobalAssetMetadata(
                UUID.randomUUID(), "00662", "富邦NASDAQ", now, "那斯達克100",
                1, now, now, null
        );
        GlobalAssetMetadata twii = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now, "臺灣50指數",
                1, now, now, null
        );

        assertThat(service.resolveBenchmarkTicker(sp500)).isEqualTo("^GSPC");
        assertThat(service.resolveBenchmarkTicker(ndx)).isEqualTo("^NDX");
        assertThat(service.resolveBenchmarkTicker(twii)).isEqualTo("^TWII");
    }

    @Test
    @DisplayName("Should execute evaluateGlobalAssetScores pipeline with dynamic R^2 classification")
    void shouldExecuteEvaluationPipelineWithDynamicClassification() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime longAgo = now.minusYears(10);

        // 0050: starts as SATELLITE, will achieve R^2 >= 0.80 and qualify for CORE
        GlobalAssetMetadata asset1 = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", longAgo, "臺灣50",
                1, now, now, null
        );

        // 006208: starts as SATELLITE, will achieve R^2 >= 0.80 and qualify for CORE
        GlobalAssetMetadata asset2 = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", longAgo, "臺灣50",
                1, now, now, null
        );

        // 00757: FANG+ satellite, stays SATELLITE (uncorrelated with TWII, high volatility >= 18%, MOM > 0)
        GlobalAssetMetadata asset3 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00757", "統一FANG+", longAgo, "FANG+",
                1, now, now, null
        );

        // 00679B: bond, starts and stays DEFENSIVE
        GlobalAssetMetadata asset4 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00679B", "元大海美債20年", longAgo, "彭博20年期以上美國公債指數",
                1, now, now, null
        );

        List<MarketDailyQuote> twiiQuotes = generateQuotesForWindow("^TWII", 20000.0, 0.0005, 0.008, 0);
        List<MarketDailyQuote> highCorrQuotes1 = generateQuotesForWindow("0050", 180.0, 0.0005, 0.008, 0);
        List<MarketDailyQuote> highCorrQuotes2 = generateQuotesForWindow("006208", 100.0, 0.0005, 0.008, 0);
        List<MarketDailyQuote> lowCorrQuotes = generateQuotesForWindow("00757", 80.0, 0.001, 0.02, 1);
        List<MarketDailyQuote> bondQuotes = generateQuotesForWindow("00679B", 30.0, 0.0001, 0.0, 3);

        when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Map.of(
                "0050", new BigDecimal("420000000000"),
                "006208", new BigDecimal("185000000000"),
                "00757", new BigDecimal("35000000000"),
                "00679B", new BigDecimal("250000000000")
        )));
        when(metadataRepository.findAll()).thenReturn(Flux.just(asset1, asset2, asset3, asset4));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^TWII")).thenReturn(Flux.fromIterable(twiiQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.fromIterable(highCorrQuotes1));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("006208")).thenReturn(Flux.fromIterable(highCorrQuotes2));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00757")).thenReturn(Flux.fromIterable(lowCorrQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00679B")).thenReturn(Flux.fromIterable(bondQuotes));

        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        StepVerifier.create(service.evaluateGlobalAssetScores())
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.evaluatedCandidatesCount()).isEqualTo(4);
                    assertThat(res.coreCount()).isEqualTo(2); // 0050, 006208 promoted to CORE
                    assertThat(res.satelliteCount()).isEqualTo(1); // 00757 remains SATELLITE
                    assertThat(res.defensiveCount()).isEqualTo(1); // 00679B remains DEFENSIVE
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should promote asset matching S&P500 benchmark to CORE")
    void shouldPromoteAssetMatchingSP500ToCore() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime longAgo = now.minusYears(8);

        // 00646 tracking S&P500
        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00646", "元大S&P500", longAgo, "標普500",
                1, now, now, null
        );

        List<MarketDailyQuote> gspcQuotes = generateQuotesForWindow("^GSPC", 5000.0, 0.0005, 0.01, 0);
        List<MarketDailyQuote> etfQuotes = generateQuotesForWindow("00646", 50.0, 0.0005, 0.01, 2);

        when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Map.of(
                "00646", new BigDecimal("35000000000")
        )));
        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^GSPC")).thenReturn(Flux.fromIterable(gspcQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("00646")).thenReturn(Flux.fromIterable(etfQuotes));

        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        StepVerifier.create(service.evaluateGlobalAssetScores())
                .assertNext(res -> {
                    assertThat(res.coreCount()).isEqualTo(1); // Promoted to CORE via ^GSPC match
                    assertThat(res.satelliteCount()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should eliminate disqualified assets so they are NOT ranked or saved to scoreRepository")
    @SuppressWarnings("unchecked")
    void shouldFilterDisqualifiedAssetsFromScoringPipelineAndNotPersistThem() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime longAgo = now.minusYears(15);

        // Qualified Core: 0050
        GlobalAssetMetadata qualifiedCore = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", longAgo, "臺灣50",
                1, now, now, null
        );

        // Disqualified Core: low AUM (< 2B TWD)
        GlobalAssetMetadata smallCore = new GlobalAssetMetadata(
                UUID.randomUUID(), "00999", "微型核心", longAgo, "臺灣50",
                1, now, now, null
        );

        // Disqualified Satellite: low AUM (< 2B TWD)
        GlobalAssetMetadata smallSatellite = new GlobalAssetMetadata(
                UUID.randomUUID(), "00991", "微型衛星", longAgo, "主題指數",
                1, now, now, null
        );

        // Disqualified Defensive: Leveraged ETF (00680L -> Stage 0 regex blocked)
        GlobalAssetMetadata leveragedBond = new GlobalAssetMetadata(
                UUID.randomUUID(), "00680L", "槓桿美債正2", longAgo, "美債正2",
                1, now, now, null
        );

        List<MarketDailyQuote> twiiQuotes = generateQuotesForWindow("^TWII", 20000.0, 0.0005, 0.01, 0);
        List<MarketDailyQuote> corrQuotes1 = generateQuotesForWindow("0050", 180.0, 0.0005, 0.01, 0);

        when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Map.of(
                "0050", new BigDecimal("420000000000"),
                "00999", new BigDecimal("1000000000"),
                "00991", new BigDecimal("1000000000"),
                "00680L", new BigDecimal("10000000000")
        )));
        when(metadataRepository.findAll()).thenReturn(Flux.just(qualifiedCore, smallCore, smallSatellite, leveragedBond));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^TWII")).thenReturn(Flux.fromIterable(twiiQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.fromIterable(corrQuotes1));

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
    @DisplayName("Should adjust stock split prices in memory during evaluation to ensure continuity")
    void shouldAdjustStockSplitPricesDuringEvaluation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime longAgo = now.minusYears(5);

        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", longAgo, "臺灣50",
                1, now, now, null
        );

        List<MarketDailyQuote> twiiQuotes = generateQuotesForWindow("^TWII", 20000.0, 0.0005, 0.008, 0);

        YearMonth ym = YearMonth.from(LocalDate.now());
        LocalDate evalDate = ym.atDay(1);
        LocalDate splitDate = evalDate.minusMonths(6);

        // Pre-split price 160.0, post-split price 40.0
        List<MarketDailyQuote> quotes = generateQuotesForWindow("0050", 160.0, 0.0005, 0.008, 0);
        // Simulate a 1-to-4 split on splitDate: post-split raw prices drop to ~40
        List<MarketDailyQuote> rawQuotesWithSplit = new ArrayList<>();
        for (MarketDailyQuote q : quotes) {
            if (!q.getTradeDate().toLocalDate().isBefore(splitDate)) {
                BigDecimal postSplitPrice = q.getClosePrice().divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP);
                rawQuotesWithSplit.add(new MarketDailyQuote(
                        q.getId(), q.getAssetId(), q.getBenchmarkId(), q.getTicker(), q.getTradeDate(),
                        postSplitPrice, postSplitPrice, postSplitPrice, postSplitPrice,
                        q.getVolumeShares() * 4, q.getTradeValueTwd(), postSplitPrice, BigDecimal.ZERO
                ));
            } else {
                rawQuotesWithSplit.add(q);
            }
        }

        CorporateAction splitAction = new CorporateAction(
                UUID.randomUUID(), asset.getId(), "0050", CorporateActionType.SPLIT,
                splitDate.atStartOfDay(), 4, 1
        );

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^TWII")).thenReturn(Flux.fromIterable(twiiQuotes));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.fromIterable(rawQuotesWithSplit));
        when(corporateActionRepository.findByEffectiveDateBetweenOrderByEffectiveDateAsc(any(), any()))
                .thenReturn(Flux.just(splitAction));
        when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Map.of("0050", new BigDecimal("400000000000"))));
        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));

        StepVerifier.create(service.evaluateGlobalAssetScores(ym.toString(), true))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.evaluatedCandidatesCount()).isEqualTo(1);
                    // R^2 with TWII should remain high (>= 0.80) because split was adjusted in memory,
                    // so 0050 correctly qualifies for CORE pool!
                    assertThat(res.coreCount()).isEqualTo(1);
                })
                .verifyComplete();
    }
}

