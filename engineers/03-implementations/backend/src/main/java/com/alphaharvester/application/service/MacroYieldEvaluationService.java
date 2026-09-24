package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.MacroYieldSnapshotRepository;
import com.alphaharvester.application.dto.MacroRegimeAssessment;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.model.CrisisLevel;
import com.alphaharvester.domain.model.MacroState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class MacroYieldEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(MacroYieldEvaluationService.class);

    private final MacroYieldSnapshotRepository macroYieldSnapshotRepository;

    public MacroYieldEvaluationService(MacroYieldSnapshotRepository macroYieldSnapshotRepository) {
        this.macroYieldSnapshotRepository = macroYieldSnapshotRepository;
    }

    public Mono<MacroRegimeAssessment> evaluateCurrentRegime() {
        return macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()
                .map(this::calculateAssessment)
                .defaultIfEmpty(fallbackAssessment())
                .doOnError(e -> log.error("Failed to evaluate macro yield regime: {}", e.getMessage(), e));
    }

    public MacroRegimeAssessment calculateAssessment(MacroYieldSnapshot snapshot) {
        BigDecimal yield = snapshot.getUsCorporateBondEffectiveYield();
        double yieldValue = yield != null ? yield.doubleValue() : 5.25;

        MacroState state;
        double equityRatio;
        double bondRatio;
        String summary;

        if (yieldValue > 5.0) {
            state = MacroState.HIGH_YIELD_ACCUMULATION;
            equityRatio = 0.80;
            bondRatio = 0.20;
            summary = "目前處於【高利蓄水期】（公司債有效殖利率 " + String.format("%.2f", yieldValue) + "% > 5.0%），建議積極配置防禦債券蓄水，股債比率 80%:20%。";
        } else if (yieldValue >= 3.5) {
            state = MacroState.NORMAL_BALANCED;
            equityRatio = 0.85;
            bondRatio = 0.15;
            summary = "目前處於【常態平衡期】（公司債有效殖利率 " + String.format("%.2f", yieldValue) + "% 介於 3.5%~5.0%），債券停止續扣、躺平領息回填股票核心，股債比率 85%:15%。";
        } else {
            state = MacroState.LOW_YIELD_HARVEST;
            equityRatio = 0.95;
            bondRatio = 0.05;
            summary = "目前處於【低利收割期】（公司債有效殖利率 " + String.format("%.2f", yieldValue) + "% < 3.5%），債券觸發停利分批出清，資金 100% 抄底股票核心，股債比率 95%:5%。";
        }

        // Determine Crisis Level from yield inversion or market shocks
        BigDecimal spread = snapshot.getYieldSpread10yMinus2y();
        CrisisLevel crisisLevel = CrisisLevel.NORMAL;
        if (spread != null && spread.doubleValue() < -0.50) {
            crisisLevel = CrisisLevel.CORRECTION;
        }

        return new MacroRegimeAssessment(state, equityRatio, bondRatio, yieldValue, summary, crisisLevel);
    }

    private MacroRegimeAssessment fallbackAssessment() {
        log.warn("No MacroYieldSnapshot found in database. Using default baseline assessment.");
        return new MacroRegimeAssessment(
                MacroState.HIGH_YIELD_ACCUMULATION,
                0.80,
                0.20,
                5.25,
                "預設基準宏觀狀態【高利蓄水期】，建議配置為 80% 股票 / 20% 債券。",
                CrisisLevel.NORMAL
        );
    }
}

