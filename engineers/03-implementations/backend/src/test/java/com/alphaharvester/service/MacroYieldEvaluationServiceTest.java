package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.MacroYieldSnapshotRepository;
import com.alphaharvester.application.dto.MacroRegimeAssessment;
import com.alphaharvester.application.service.MacroYieldEvaluationService;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CrisisLevel;
import com.alphaharvester.domain.model.MacroState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroYieldEvaluationServiceTest {

    @Mock
    private MacroYieldSnapshotRepository macroYieldSnapshotRepository;

    @Mock
    private com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository quoteRepository;

    @Mock
    private com.alphaharvester.adapter.out.persistence.CorporateActionRepository corporateActionRepository;

    private MacroYieldEvaluationService macroYieldEvaluationService;

    @BeforeEach
    void setUp() {
        macroYieldEvaluationService = new MacroYieldEvaluationService(
                macroYieldSnapshotRepository, quoteRepository, corporateActionRepository
        );
    }

    @Test
    @DisplayName("Should evaluate HIGH_YIELD_ACCUMULATION when YTM > 5.0%")
    void shouldEvaluateHighYieldAccumulation() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null,
                LocalDateTime.now(),
                new BigDecimal("5.45"),
                new BigDecimal("4.20"),
                new BigDecimal("4.50"),
                new BigDecimal("0.10")
        );

        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(snapshot);

        assertThat(assessment.macroState()).isEqualTo(MacroState.HIGH_YIELD_ACCUMULATION);
        assertThat(assessment.recommendedEquityRatio()).isEqualTo(0.80);
        assertThat(assessment.recommendedBondRatio()).isEqualTo(0.20);
        assertThat(assessment.usCorporateBondYield()).isEqualTo(5.45);
        assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.NORMAL);
        assertThat(assessment.assessmentSummary()).contains("高利蓄水期");
    }

    @Test
    @DisplayName("Should evaluate NORMAL_BALANCED when 3.5% <= YTM <= 5.0%")
    void shouldEvaluateNormalBalanced() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null,
                LocalDateTime.now(),
                new BigDecimal("4.25"),
                new BigDecimal("3.80"),
                new BigDecimal("4.10"),
                new BigDecimal("0.20")
        );

        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(snapshot);

        assertThat(assessment.macroState()).isEqualTo(MacroState.NORMAL_BALANCED);
        assertThat(assessment.recommendedEquityRatio()).isEqualTo(0.85);
        assertThat(assessment.recommendedBondRatio()).isEqualTo(0.15);
        assertThat(assessment.usCorporateBondYield()).isEqualTo(4.25);
        assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.NORMAL);
        assertThat(assessment.assessmentSummary()).contains("常態平衡期");
    }

    @Test
    @DisplayName("Should evaluate LOW_YIELD_HARVEST when YTM < 3.5%")
    void shouldEvaluateLowYieldHarvest() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null,
                LocalDateTime.now(),
                new BigDecimal("3.20"),
                new BigDecimal("2.80"),
                new BigDecimal("3.00"),
                new BigDecimal("0.30")
        );

        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(snapshot);

        assertThat(assessment.macroState()).isEqualTo(MacroState.LOW_YIELD_HARVEST);
        assertThat(assessment.recommendedEquityRatio()).isEqualTo(0.95);
        assertThat(assessment.recommendedBondRatio()).isEqualTo(0.05);
        assertThat(assessment.usCorporateBondYield()).isEqualTo(3.20);
        assertThat(assessment.assessmentSummary()).contains("低利收割期");
    }

    @Test
    @DisplayName("Should detect CORRECTION crisis level when yield spread < -0.50%")
    void shouldDetectCorrectionOnInversion() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null,
                LocalDateTime.now(),
                new BigDecimal("5.25"),
                new BigDecimal("3.80"),
                new BigDecimal("4.10"),
                new BigDecimal("-0.65")
        );

        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(snapshot);

        assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.CORRECTION);
    }

    @Test
    @DisplayName("Should return assessment from repository Mono")
    void shouldEvaluateCurrentRegimeFromRepository() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null,
                LocalDateTime.now(),
                new BigDecimal("5.30"),
                new BigDecimal("4.10"),
                new BigDecimal("4.40"),
                new BigDecimal("0.05")
        );
        when(macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snapshot));
        when(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(any(), any()))
                .thenReturn(Flux.empty());
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(macroYieldEvaluationService.evaluateCurrentRegime())
                .assertNext(assessment -> {
                    assertThat(assessment.macroState()).isEqualTo(MacroState.HIGH_YIELD_ACCUMULATION);
                    assertThat(assessment.usCorporateBondYield()).isEqualTo(5.30);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should detect CRISIS_LEVEL_2 when drawdown <= -30%")
    void shouldDetectCrisisLevel2OnSevereDrawdown() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, LocalDateTime.now(), new BigDecimal("4.50"), new BigDecimal("3.80"), new BigDecimal("4.10"), new BigDecimal("0.20")
        );
        MarketDailyQuote highQuote = new MarketDailyQuote();
        highQuote.setClosePrice(new BigDecimal("100.0"));
        MarketDailyQuote currentQuote = new MarketDailyQuote();
        currentQuote.setClosePrice(new BigDecimal("68.0")); // -32% drawdown

        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(
                snapshot, List.of(currentQuote, highQuote), null
        );

        assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.CRISIS_LEVEL_2);
        assertThat(assessment.assessmentSummary()).contains("CRISIS_LEVEL_2");
    }

    @Test
    @DisplayName("Should detect CRISIS_LEVEL_1 when drawdown <= -15% and VIX >= 30")
    void shouldDetectCrisisLevel1OnPanicAndDrawdown() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, LocalDateTime.now(), new BigDecimal("4.50"), new BigDecimal("3.80"), new BigDecimal("4.10"), new BigDecimal("0.20")
        );
        MarketDailyQuote highQuote = new MarketDailyQuote();
        highQuote.setClosePrice(new BigDecimal("100.0"));
        MarketDailyQuote currentQuote = new MarketDailyQuote();
        currentQuote.setClosePrice(new BigDecimal("82.0")); // -18% drawdown

        MarketDailyQuote vixQuote = new MarketDailyQuote();
        vixQuote.setClosePrice(new BigDecimal("32.5")); // VIX >= 30

        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(
                snapshot, List.of(currentQuote, highQuote), vixQuote
        );

        assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.CRISIS_LEVEL_1);
        assertThat(assessment.assessmentSummary()).contains("CRISIS_LEVEL_1");
    }

    @Test
    @DisplayName("Should detect CORRECTION when drawdown <= -10% but not meeting Level 1")
    void shouldDetectCorrectionOnModerateDrawdown() {
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, LocalDateTime.now(), new BigDecimal("4.50"), new BigDecimal("3.80"), new BigDecimal("4.10"), new BigDecimal("0.20")
        );
        MarketDailyQuote highQuote = new MarketDailyQuote();
        highQuote.setClosePrice(new BigDecimal("100.0"));
        MarketDailyQuote currentQuote = new MarketDailyQuote();
        currentQuote.setClosePrice(new BigDecimal("88.0")); // -12% drawdown

        MarketDailyQuote vixQuote = new MarketDailyQuote();
        vixQuote.setClosePrice(new BigDecimal("22.0")); // VIX < 30

        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(
                snapshot, List.of(currentQuote, highQuote), vixQuote
        );

        assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.CORRECTION);
        assertThat(assessment.assessmentSummary()).contains("CORRECTION");
    }

    @Test
    @DisplayName("Should complete empty when repository is empty")
    void shouldCompleteEmptyWhenRepositoryEmpty() {
        when(macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.empty());

        StepVerifier.create(macroYieldEvaluationService.evaluateCurrentRegime())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should prioritize ^TWII over 0050 in evaluateCurrentRegime")
    void shouldPrioritizeTwiiOver0050InEvaluateCurrentRegime() {
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, now, new BigDecimal("4.50"), new BigDecimal("3.80"), new BigDecimal("4.10"), new BigDecimal("0.20")
        );

        MarketDailyQuote twiiQuote = new MarketDailyQuote();
        twiiQuote.setTicker("^TWII");
        twiiQuote.setClosePrice(new BigDecimal("22000.0"));
        twiiQuote.setTradeDate(now);

        MarketDailyQuote vixQuote = new MarketDailyQuote();
        vixQuote.setTicker("^VIX");
        vixQuote.setClosePrice(new BigDecimal("18.0"));
        vixQuote.setTradeDate(now);

        when(macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snapshot));
        when(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq("^TWII"), any()))
                .thenReturn(reactor.core.publisher.Flux.just(twiiQuote));
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc("^VIX")).thenReturn(Mono.just(vixQuote));

        StepVerifier.create(macroYieldEvaluationService.evaluateCurrentRegime())
                .assertNext(assessment -> {
                    assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.NORMAL);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fallback to 0050 and adjust splits to prevent false CRISIS_LEVEL_2")
    void shouldFallbackTo0050AndAdjustSplitsToPreventFalseCrisis() {
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, now, new BigDecimal("4.50"), new BigDecimal("3.80"), new BigDecimal("4.10"), new BigDecimal("0.20")
        );

        // Post-split 0050 quote at 50.0 TWD
        MarketDailyQuote postSplitQuote = new MarketDailyQuote(null, null, null, "0050", now,
                new BigDecimal("50.0"), new BigDecimal("50.0"), new BigDecimal("50.0"),
                new BigDecimal("50.0"), 1000000L, new BigDecimal("50000000"), null, null);

        // Pre-split 0050 quote from 10 days ago at 200.0 TWD
        MarketDailyQuote preSplitQuote = new MarketDailyQuote(null, null, null, "0050", now.minusDays(10),
                new BigDecimal("200.0"), new BigDecimal("200.0"), new BigDecimal("200.0"),
                new BigDecimal("200.0"), 1000000L, new BigDecimal("200000000"), null, null);

        com.alphaharvester.domain.entity.CorporateAction splitAction = new com.alphaharvester.domain.entity.CorporateAction(
                null, null, "0050", com.alphaharvester.domain.model.CorporateActionType.SPLIT,
                now.minusDays(5), 4, 1
        );

        MarketDailyQuote vixQuote = new MarketDailyQuote();
        vixQuote.setTicker("^VIX");
        vixQuote.setClosePrice(new BigDecimal("18.0"));
        vixQuote.setTradeDate(now);

        when(macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snapshot));
        when(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq("^TWII"), any()))
                .thenReturn(reactor.core.publisher.Flux.empty());
        when(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq("0050"), any()))
                .thenReturn(reactor.core.publisher.Flux.just(postSplitQuote, preSplitQuote));
        when(corporateActionRepository.findByTicker("0050")).thenReturn(reactor.core.publisher.Flux.just(splitAction));
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc("^VIX")).thenReturn(Mono.just(vixQuote));

        // When split is adjusted: preSplitQuote becomes 200.0 * (1/4) = 50.0 -> drawdown = 0% -> NORMAL crisis level
        StepVerifier.create(macroYieldEvaluationService.evaluateCurrentRegime())
                .assertNext(assessment -> {
                    assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.NORMAL);
                    assertThat(assessment.assessmentSummary()).doesNotContain("CRISIS_LEVEL_2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should strictly exclude quotes older than 365 calendar days in TAIEX 52-week drawdown")
    void shouldExcludeQuotesOlderThan365CalendarDaysInTaiexDrawdown() {
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, now, new BigDecimal("4.50"), new BigDecimal("3.80"), new BigDecimal("4.10"), new BigDecimal("0.20")
        );

        // Current TWII quote at 20,000
        MarketDailyQuote currentQuote = new MarketDailyQuote();
        currentQuote.setTicker("^TWII");
        currentQuote.setClosePrice(new BigDecimal("20000.0"));
        currentQuote.setTradeDate(now);

        // Within 52 weeks (100 days ago) at 21,000 (drawdown ~ -4.7% -> NORMAL)
        MarketDailyQuote quote100d = new MarketDailyQuote();
        quote100d.setTicker("^TWII");
        quote100d.setClosePrice(new BigDecimal("21000.0"));
        quote100d.setTradeDate(now.minusDays(100));

        // Beyond 52 weeks (380 days ago) at 35,000! (If leaked, drawdown = -42.8% -> would trigger false CRISIS_LEVEL_2!)
        MarketDailyQuote quote380d = new MarketDailyQuote();
        quote380d.setTicker("^TWII");
        quote380d.setClosePrice(new BigDecimal("35000.0"));
        quote380d.setTradeDate(now.minusDays(380));

        MarketDailyQuote vixQuote = new MarketDailyQuote();
        vixQuote.setTicker("^VIX");
        vixQuote.setClosePrice(new BigDecimal("18.0"));
        vixQuote.setTradeDate(now);

        when(macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snapshot));
        when(quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq("^TWII"), any()))
                .thenReturn(reactor.core.publisher.Flux.just(currentQuote, quote100d, quote380d));
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc("^VIX"))
                .thenReturn(Mono.just(vixQuote));

        // When 365 calendar days rule is enforced:
        // quote380d is excluded from maxPrice!
        // maxPrice is 21,000 -> drawdown = -4.7% -> NORMAL (not CRISIS_LEVEL_2)
        StepVerifier.create(macroYieldEvaluationService.evaluateCurrentRegime())
                .assertNext(assessment -> {
                    assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.NORMAL);
                    assertThat(assessment.assessmentSummary()).doesNotContain("CRISIS_LEVEL_2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should correctly include exactly 1-year ago quote during leap year (366 calendar days) in TAIEX drawdown")
    void shouldHandleLeapYearCorrectlyInTaiexDrawdown() {
        LocalDateTime leapYearNow = LocalDateTime.of(2024, 3, 1, 13, 30, 0);
        LocalDateTime exactlyOneYearAgo = LocalDateTime.of(2023, 3, 1, 13, 30, 0);

        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, leapYearNow, new BigDecimal("4.50"), new BigDecimal("3.80"), new BigDecimal("4.10"), new BigDecimal("0.20")
        );

        MarketDailyQuote currentQuote = new MarketDailyQuote();
        currentQuote.setTicker("^TWII");
        currentQuote.setClosePrice(new BigDecimal("20000.0"));
        currentQuote.setTradeDate(leapYearNow);

        MarketDailyQuote peakQuoteOneYearAgo = new MarketDailyQuote();
        peakQuoteOneYearAgo.setTicker("^TWII");
        peakQuoteOneYearAgo.setClosePrice(new BigDecimal("30000.0"));
        peakQuoteOneYearAgo.setTradeDate(exactlyOneYearAgo);

        MarketDailyQuote vixQuote = new MarketDailyQuote();
        vixQuote.setTicker("^VIX");
        vixQuote.setClosePrice(new BigDecimal("18.0"));
        vixQuote.setTradeDate(leapYearNow);

        // evaluate pure assessment
        MacroRegimeAssessment assessment = macroYieldEvaluationService.calculateAssessment(
                snapshot, List.of(currentQuote, peakQuoteOneYearAgo), vixQuote
        );

        // Drawdown = (20000 - 30000) / 30000 = -33.3% <= -30% -> triggers CRISIS_LEVEL_2
        assertThat(assessment.crisisLevel()).isEqualTo(CrisisLevel.CRISIS_LEVEL_2);
        assertThat(assessment.assessmentSummary()).contains("CRISIS_LEVEL_2");
    }
}

