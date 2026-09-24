package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.service.GlobalAssetScoreEvaluationService;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.GlobalAssetScore;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalAssetScoreEvaluationServiceTest {

    @Mock
    private GlobalAssetMetadataRepository metadataRepository;

    @Mock
    private GlobalAssetScoreRepository scoreRepository;

    private GlobalAssetScoreEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new GlobalAssetScoreEvaluationService(metadataRepository, scoreRepository);
    }

    @Test
    @DisplayName("Should evaluate and qualify Core asset with low expense ratio and high AUM")
    void shouldEvaluateQualifiedCoreAsset() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata coreAsset = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", now.minusYears(8), "臺灣50", "富邦投信",
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
                UUID.randomUUID(), "00999", "昂貴大盤ETF", now.minusYears(3), "某大盤指數", "某投信",
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
                UUID.randomUUID(), "00998", "剛掛牌ETF", now.minusDays(10), "某指數", "某投信",
                new BigDecimal("0.0030"), new BigDecimal("15000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.NONE, 1, now, now, null
        );

        GlobalAssetScore score = service.evaluateAsset(youngAsset, now);

        assertThat(score.getIsQualified()).isFalse();
        assertThat(score.getDisqualificationReason()).contains("未滿 30 個交易日");
    }

    @Test
    @DisplayName("Should execute evaluateGlobalAssetScores pipeline and rank assets independently")
    void shouldExecuteEvaluationPipeline() {
        LocalDateTime now = LocalDateTime.now();
        GlobalAssetMetadata core1 = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now.minusYears(15), "臺灣50", "元大投信",
                new BigDecimal("0.0043"), new BigDecimal("420000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );
        GlobalAssetMetadata core2 = new GlobalAssetMetadata(
                UUID.randomUUID(), "006208", "富邦台50", now.minusYears(10), "臺灣50", "富邦投信",
                new BigDecimal("0.0024"), new BigDecimal("185000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );
        GlobalAssetMetadata sat1 = new GlobalAssetMetadata(
                UUID.randomUUID(), "00757", "統一FANG+", now.minusYears(5), "FANG+", "統一投信",
                new BigDecimal("0.0060"), new BigDecimal("35000000000"),
                CandidateAssetClass.SATELLITE, DistributionFrequency.NONE, 1, now, now, null
        );

        when(metadataRepository.findAll()).thenReturn(Flux.just(core1, core2, sat1));
        when(scoreRepository.saveAll(anyList())).thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));

        StepVerifier.create(service.evaluateGlobalAssetScores())
                .assertNext(res -> {
                    assertThat(res.status()).isEqualTo("SUCCESS");
                    assertThat(res.evaluatedCandidatesCount()).isEqualTo(3);
                    assertThat(res.coreCount()).isEqualTo(2);
                    assertThat(res.satelliteCount()).isEqualTo(1);
                    assertThat(res.defensiveCount()).isEqualTo(0);
                })
                .verifyComplete();
    }
}

