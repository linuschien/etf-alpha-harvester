package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.application.service.GlobalAssetScoreEvaluationService;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.OrthogonalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StageScreeningAndRankingIntegrationTest {

    private static final Pattern STAGE_0_ALLOW_REGEX = Pattern.compile("^00\\d{2,4}B?$");

    @Mock private GlobalAssetMetadataRepository metadataRepository;
    @Mock private GlobalAssetScoreRepository scoreRepository;
    @Mock private MarketDailyQuoteRepository quoteRepository;
    @Mock private DcaPopularityRankRepository dcaRankRepository;
    @Mock private DividendAnnouncementRepository dividendRepository;
    @Mock private DataFeedSyncWatermarkRepository watermarkRepository;
    @Mock private GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository;
    @Mock private BenchmarkIndexRepository benchmarkRepository;
    @Mock private MacroYieldSnapshotRepository macroYieldRepository;
    @Mock private CorporateActionRepository corporateActionRepository;
    @Mock private ExternalMarketDataPort externalMarketDataPort;

    private GlobalAssetScoreEvaluationService evaluationService;
    private GlobalAssetQueryService queryService;

    @BeforeEach
    void setUp() {
        evaluationService = new GlobalAssetScoreEvaluationService(
                metadataRepository, scoreRepository, quoteRepository,
                dcaRankRepository, dividendRepository, watermarkRepository, pairwiseMatrixRepository,
                externalMarketDataPort, corporateActionRepository
        );

        queryService = new GlobalAssetQueryService(
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, scoreRepository, dcaRankRepository,
                dividendRepository, corporateActionRepository, pairwiseMatrixRepository
        );

        when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Collections.emptyMap()));

        when(dcaRankRepository.findAll()).thenReturn(Flux.empty());
        when(dividendRepository.findByExDateBetweenOrderByExDateAsc(any(), any())).thenReturn(Flux.empty());
        when(corporateActionRepository.findByEffectiveDateBetweenOrderByEffectiveDateAsc(any(), any())).thenReturn(Flux.empty());
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(any(), any(), any())).thenReturn(Flux.empty());
        when(metadataRepository.findAll()).thenReturn(Flux.empty());
        when(watermarkRepository.findByFeedName(any())).thenReturn(Mono.empty());
        when(watermarkRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(scoreRepository.deleteByEvaluationDate(any())).thenReturn(Mono.empty());
        when(pairwiseMatrixRepository.deleteByEvaluationDate(any())).thenReturn(Mono.empty());
        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));
        when(pairwiseMatrixRepository.saveAll(anyList())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));
    }

    private List<MarketDailyQuote> generateCalendarQuotes(String ticker, YearMonth targetYm, double startPrice, double growthRate, double noiseFactor, int pattern) {
        LocalDate evalDate = targetYm.atDay(1);
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
                    // High volatility satellite (vol ~ 35%, MOM > 0, zero corr with pattern 0)
                    factor = 1.0 + ((i % 4 < 2) ? 0.025 : -0.018);
                } else if (pattern == 2) {
                    // Shift-1 follower for US benchmark
                    double noise = ((i - 1) % 2 == 0) ? noiseFactor : -noiseFactor;
                    factor = 1.0 + growthRate + noise;
                } else {
                    factor = 1.0 + growthRate;
                }
                p *= factor;
                quotes.add(new MarketDailyQuote(
                        UUID.randomUUID(), UUID.randomUUID(), null, ticker, curr.atTime(13, 30),
                        BigDecimal.valueOf(p), BigDecimal.valueOf(p * 1.01), BigDecimal.valueOf(p * 0.99),
                        BigDecimal.valueOf(p), 1_000_000L, BigDecimal.valueOf(50_000_000L)
                ));
                i++;
            }
            curr = curr.plusDays(1);
        }
        return quotes;
    }

    @Test
    @DisplayName("Stage 0: Verify allowlist allows only pure digits and B suffix, blocks Leveraged (L), Inverse (R), Futures (U), Active (A/D), Currency (K/C), Balanced (T), ETN (02xxxx)")
    void shouldVerifyStage0Allowlist() {
        // Disqualified
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00631L").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00632R").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00642U").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00940A").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00980D").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00668K").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00980T").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("020001").matches()).isFalse();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("020015").matches()).isFalse();

        // Qualified prototype ETFs (pure digits or ending in B)
        assertThat(STAGE_0_ALLOW_REGEX.matcher("0050").matches()).isTrue();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("006208").matches()).isTrue();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00679B").matches()).isTrue();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00757").matches()).isTrue();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00713").matches()).isTrue();
        assertThat(STAGE_0_ALLOW_REGEX.matcher("00878").matches()).isTrue();
    }

    @Test
    @DisplayName("Stage 1 Universal Gatekeepers: Reject if listing age < 365d, trading days < 220, AUM < 2B, or turnover < 20M")
    void shouldEnforceUniversalGatekeepers() {
        YearMonth targetYm = YearMonth.parse("2026-09");
        LocalDate evalDate = targetYm.atDay(1);
        LocalDateTime evalDateTime = evalDate.atStartOfDay();
        LocalDate cutoffDate = evalDate.minusDays(1);

        // 1. Too young (listing < 365d)
        GlobalAssetMetadata youngAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00940", "元大台灣價值高息", cutoffDate.minusMonths(6).atStartOfDay(),
                "臺灣價值高息", 1, evalDateTime, evalDateTime, null
        );

        // 2. Low AUM (< 2B)
        GlobalAssetMetadata lowAumAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00998", "微型ETF", cutoffDate.minusYears(2).atStartOfDay(),
                "某指數", 1, evalDateTime, evalDateTime, null
        );

        // 3. Insufficient trading days (< 220)
        GlobalAssetMetadata lowTradingDaysAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00997", "交易日不足ETF", cutoffDate.minusYears(2).atStartOfDay(),
                "某指數", 1, evalDateTime, evalDateTime, null
        );

        // 4. Low liquidity (30d turnover < 20M)
        GlobalAssetMetadata lowTurnoverAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "00996", "低流動性ETF", cutoffDate.minusYears(2).atStartOfDay(),
                "某指數", 1, evalDateTime, evalDateTime, null
        );

        when(metadataRepository.findAll()).thenReturn(Flux.just(youngAsset, lowAumAsset, lowTradingDaysAsset, lowTurnoverAsset));
        when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Map.of(
                "00940", new BigDecimal("50000000000"),
                "00998", new BigDecimal("1500000000"),
                "00997", new BigDecimal("10000000000"),
                "00996", new BigDecimal("10000000000")
        )));

        // 20 quotes only (< 220) for lowTradingDaysAsset
        List<MarketDailyQuote> shortQuotes = generateCalendarQuotes("00997", targetYm, 50.0, 0.0005, 0.01, 0).subList(0, 50);
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(eq("00997"), any(), any())).thenReturn(Flux.fromIterable(shortQuotes));

        // Low turnover quotes for lowTurnoverAsset
        List<MarketDailyQuote> illiquidQuotes = generateCalendarQuotes("00996", targetYm, 50.0, 0.0005, 0.01, 0).stream()
                .map(q -> new MarketDailyQuote(q.getId(), q.getAssetId(), q.getBenchmarkId(), q.getTicker(), q.getTradeDate(),
                        q.getOpenPrice(), q.getHighPrice(), q.getLowPrice(), q.getClosePrice(), 100_000L, BigDecimal.valueOf(5_000_000L)))
                .toList();
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(eq("00996"), any(), any())).thenReturn(Flux.fromIterable(illiquidQuotes));

        StepVerifier.create(evaluationService.evaluateGlobalAssetScores("2026-09", true))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("FAILED");
                    assertThat(res.evaluatedCandidatesCount()).isEqualTo(0);
                    assertThat(res.message()).contains("Zero candidate scores produced");
                })
                .verifyComplete();

        verify(scoreRepository, never()).deleteByEvaluationDate(any());
        verify(pairwiseMatrixRepository, never()).deleteByEvaluationDate(any());
    }

    @Test
    @DisplayName("Stage 2 & Stage 3: Mode A Greedy Orthogonal Engine with Top 10 Core and Top 20 Satellites")
    @SuppressWarnings("unchecked")
    void shouldExecuteScoringAndModeAGreedyOrthogonalEngine() {
        YearMonth targetYm = YearMonth.parse("2026-09");
        LocalDate evalDate = targetYm.atDay(1);
        LocalDateTime evalDateTime = evalDate.atStartOfDay();
        LocalDate cutoffDate = evalDate.minusDays(1);
        LocalDateTime longAgo = cutoffDate.minusYears(5).atStartOfDay();

        // Core 1: 0050 (AUM 420B, high correlation with ^TWII)
        GlobalAssetMetadata core1 = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", longAgo, "臺灣50",
                1, evalDateTime, evalDateTime, null
        );

        // Core 2: 006208 (AUM 185B, collinear with 0050, R^2 ~ 1.0 >= 0.50 -> Should be REJECTED_COLLINEAR)
        GlobalAssetMetadata core2 = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", longAgo, "臺灣50",
                1, evalDateTime, evalDateTime, null
        );

        // Satellite 1: 00757 (vol >= 18%, MOM > 0, zero corr with ^TWII)
        GlobalAssetMetadata sat1 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00757", "統一FANG+", longAgo, "FANG+",
                1, evalDateTime, evalDateTime, null
        );

        // Bond 1: 00679B (Defensive bond, flat/independent from TWII)
        GlobalAssetMetadata bond1 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00679B", "元大海美債20年", longAgo, "美債20年",
                1, evalDateTime, evalDateTime, null
        );

        List<MarketDailyQuote> twiiQuotes = generateCalendarQuotes("^TWII", targetYm, 20000.0, 0.0005, 0.008, 0);
        List<MarketDailyQuote> core1Quotes = generateCalendarQuotes("0050", targetYm, 180.0, 0.0005, 0.008, 0);
        List<MarketDailyQuote> core2Quotes = generateCalendarQuotes("006208", targetYm, 100.0, 0.0005, 0.008, 0);
        List<MarketDailyQuote> sat1Quotes = generateCalendarQuotes("00757", targetYm, 80.0, 0.001, 0.02, 1);
        List<MarketDailyQuote> bond1Quotes = generateCalendarQuotes("00679B", targetYm, 30.0, 0.0001, 0.0, 3);

        when(externalMarketDataPort.fetchCurrentAumMap()).thenReturn(Mono.just(Map.of(
                "0050", new BigDecimal("420000000000"),
                "006208", new BigDecimal("185000000000"),
                "00757", new BigDecimal("35000000000"),
                "00679B", new BigDecimal("250000000000")
        )));
        when(metadataRepository.findAll()).thenReturn(Flux.just(core1, core2, sat1, bond1));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(eq("^TWII"), any(), any())).thenReturn(Flux.fromIterable(twiiQuotes));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(eq("0050"), any(), any())).thenReturn(Flux.fromIterable(core1Quotes));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(eq("006208"), any(), any())).thenReturn(Flux.fromIterable(core2Quotes));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(eq("00757"), any(), any())).thenReturn(Flux.fromIterable(sat1Quotes));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(eq("00679B"), any(), any())).thenReturn(Flux.fromIterable(bond1Quotes));

        ArgumentCaptor<List<GlobalAssetScore>> scoreCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<GlobalAssetPairwiseMatrix>> matrixCaptor = ArgumentCaptor.forClass(List.class);

        StepVerifier.create(evaluationService.evaluateGlobalAssetScores("2026-09", true))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.evaluatedCandidatesCount()).isEqualTo(4);
                    assertThat(res.coreCount()).isEqualTo(2);
                    assertThat(res.satelliteCount()).isEqualTo(1);
                    assertThat(res.defensiveCount()).isEqualTo(1);
                })
                .verifyComplete();

        verify(scoreRepository).saveAll(scoreCaptor.capture());
        verify(pairwiseMatrixRepository).saveAll(matrixCaptor.capture());

        List<GlobalAssetScore> savedScores = scoreCaptor.getValue();
        List<GlobalAssetPairwiseMatrix> savedMatrix = matrixCaptor.getValue();

        // Verify DB saved scores contain pure intrinsic factor ranks without persistent orthogonal pollution
        GlobalAssetScore score0050 = savedScores.stream().filter(s -> "0050".equals(s.getTicker())).findFirst().orElseThrow();
        GlobalAssetScore score006208 = savedScores.stream().filter(s -> "006208".equals(s.getTicker())).findFirst().orElseThrow();

        assertThat(score0050.getClassRank()).isEqualTo(1);
        assertThat(score006208.getClassRank()).isEqualTo(2);
        assertThat(score0050.getOrthogonalStatus()).isNull();
        assertThat(score006208.getOrthogonalStatus()).isNull();

        // Verify Pairwise Matrix contains strictly sorted pair: base_ticker < target_ticker ("0050" < "006208")
        assertThat(savedMatrix).isNotEmpty();
        GlobalAssetPairwiseMatrix pair = savedMatrix.stream()
                .filter(m -> "0050".equals(m.getBaseTicker()) && "006208".equals(m.getTargetTicker()))
                .findFirst().orElseThrow();
        assertThat(pair.getRSquared().doubleValue()).isGreaterThanOrEqualTo(0.99);
    }

    @Test
    @DisplayName("Stage 3 Dynamic Orthogonalization: Mode A (Rank 1 Seed) & Mode B (Custom Anchor Seed) via getOrthogonalCandidates")
    void shouldRecalculateModeAAndModeBOrthogonalizationDynamically() {
        LocalDateTime evalDateTime = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // Simulate 0050 (Rank 1) and 006208 (Rank 2) in Core Pool
        GlobalAssetScore s1 = new GlobalAssetScore();
        s1.setTicker("0050");
        s1.setClassRank(1);
        s1.setAssetClass(CandidateAssetClass.CORE);
        s1.setEvaluationDate(evalDateTime);

        GlobalAssetScore s2 = new GlobalAssetScore();
        s2.setTicker("006208");
        s2.setClassRank(2);
        s2.setAssetClass(CandidateAssetClass.CORE);
        s2.setEvaluationDate(evalDateTime);

        // Pairwise matrix entry: strictly sorted base < target ("0050", "006208") with R^2 = 0.99
        GlobalAssetPairwiseMatrix m1 = new GlobalAssetPairwiseMatrix(
                UUID.randomUUID(), evalDateTime, CandidateAssetClass.CORE, "0050", "006208", new BigDecimal("0.9900"), new BigDecimal("0.9950")
        );

        when(scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(eq(CandidateAssetClass.CORE), any()))
                .thenReturn(Flux.just(s1, s2));
        when(pairwiseMatrixRepository.findByEvaluationDateAndAssetClass(any(), eq(CandidateAssetClass.CORE)))
                .thenReturn(Flux.just(m1));

        // 1. Mode A: seedTicker = null -> Defaults to Rank 1 (0050) as Seed, 006208 is REJECTED_COLLINEAR
        StepVerifier.create(queryService.getOrthogonalCandidates(CandidateAssetClass.CORE, null, null))
                .assertNext(first -> {
                    assertThat(first.getTicker()).isEqualTo("0050");
                    assertThat(first.getOrthogonalStatus()).isEqualTo(OrthogonalStatus.ACCEPTED);
                    assertThat(first.getCollisionDetail()).contains("Seed (Rank 1)");
                })
                .assertNext(second -> {
                    assertThat(second.getTicker()).isEqualTo("006208");
                    assertThat(second.getOrthogonalStatus()).isEqualTo(OrthogonalStatus.REJECTED_COLLINEAR);
                    assertThat(second.getCollisionDetail()).contains("Collinear with 0050");
                })
                .verifyComplete();

        // 2. Mode B: When user anchors 006208 as Seed:
        // 006208 becomes Seed (ACCEPTED), and 0050 becomes REJECTED_COLLINEAR!
        StepVerifier.create(queryService.getOrthogonalCandidates(CandidateAssetClass.CORE, "006208", null))
                .assertNext(first -> {
                    assertThat(first.getTicker()).isEqualTo("006208");
                    assertThat(first.getOrthogonalStatus()).isEqualTo(OrthogonalStatus.ACCEPTED);
                    assertThat(first.getCollisionDetail()).contains("Anchor Seed (Mode B)");
                })
                .assertNext(second -> {
                    assertThat(second.getTicker()).isEqualTo("0050");
                    assertThat(second.getOrthogonalStatus()).isEqualTo(OrthogonalStatus.REJECTED_COLLINEAR);
                    assertThat(second.getCollisionDetail()).contains("Collinear with 006208");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Monthly Cadence: Skip if Watermark PASS and force=false; execute if force=true")
    void shouldHonorMonthlyWatermarkBypassAndForceOverride() {
        YearMonth targetYm = YearMonth.parse("2026-09");
        LocalDate evalDate = targetYm.atDay(1);
        LocalDateTime evalDateTime = evalDate.atStartOfDay();

        DataFeedSyncWatermark passWatermark = new DataFeedSyncWatermark(
                UUID.randomUUID(), "MONTHLY_TOP_LIST", evalDateTime, evalDateTime, 20, "PASS", null, LocalDateTime.now()
        );

        when(watermarkRepository.findByFeedName("MONTHLY_TOP_LIST")).thenReturn(Mono.just(passWatermark));
        when(scoreRepository.findByEvaluationDateOrderByClassRankAsc(evalDateTime)).thenReturn(Flux.empty());

        // Call with force = false -> Expect SKIPPED
        StepVerifier.create(evaluationService.evaluateGlobalAssetScores("2026-09", false))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SKIPPED");
                    assertThat(res.message()).contains("Watermark 斷路跳過重複運算");
                })
                .verifyComplete();

        // Call with force = true -> Expect execution proceeds
        when(metadataRepository.findAll()).thenReturn(Flux.empty());
        StepVerifier.create(evaluationService.evaluateGlobalAssetScores("2026-09", true))
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                })
                .verifyComplete();
    }
}
