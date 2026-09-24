package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.MacroYieldSnapshotRepository;
import com.alphaharvester.application.dto.MacroRegimeAssessment;
import com.alphaharvester.application.service.MacroYieldEvaluationService;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.model.CrisisLevel;
import com.alphaharvester.domain.model.MacroState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroYieldEvaluationServiceTest {

    @Mock
    private MacroYieldSnapshotRepository macroYieldSnapshotRepository;

    private MacroYieldEvaluationService macroYieldEvaluationService;

    @BeforeEach
    void setUp() {
        macroYieldEvaluationService = new MacroYieldEvaluationService(macroYieldSnapshotRepository);
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

        StepVerifier.create(macroYieldEvaluationService.evaluateCurrentRegime())
                .assertNext(assessment -> {
                    assertThat(assessment.macroState()).isEqualTo(MacroState.HIGH_YIELD_ACCUMULATION);
                    assertThat(assessment.usCorporateBondYield()).isEqualTo(5.30);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return fallback assessment when repository is empty")
    void shouldReturnFallbackWhenRepositoryEmpty() {
        when(macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.empty());

        StepVerifier.create(macroYieldEvaluationService.evaluateCurrentRegime())
                .assertNext(assessment -> {
                    assertThat(assessment.macroState()).isEqualTo(MacroState.HIGH_YIELD_ACCUMULATION);
                    assertThat(assessment.recommendedEquityRatio()).isEqualTo(0.80);
                })
                .verifyComplete();
    }
}

