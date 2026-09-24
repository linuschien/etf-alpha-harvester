package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.MacroYieldSnapshotRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.MacroRegimeAssessment;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CrisisLevel;
import com.alphaharvester.domain.model.MacroState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class MacroYieldEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(MacroYieldEvaluationService.class);

    private final MacroYieldSnapshotRepository macroYieldSnapshotRepository;
    private final MarketDailyQuoteRepository quoteRepository;

    @Autowired
    public MacroYieldEvaluationService(MacroYieldSnapshotRepository macroYieldSnapshotRepository,
                                       @Autowired(required = false) MarketDailyQuoteRepository quoteRepository) {
        this.macroYieldSnapshotRepository = macroYieldSnapshotRepository;
        this.quoteRepository = quoteRepository;
    }

    public MacroYieldEvaluationService(MacroYieldSnapshotRepository macroYieldSnapshotRepository) {
        this(macroYieldSnapshotRepository, null);
    }

    public Mono<MacroRegimeAssessment> evaluateCurrentRegime() {
        return macroYieldSnapshotRepository.findTopByOrderByRecordDateDesc()
                .flatMap(snapshot -> {
                    if (quoteRepository == null) {
                        return Mono.just(calculateAssessment(snapshot, null, null));
                    }
                    Mono<List<MarketDailyQuote>> coreQuotesMono = quoteRepository.findByTickerOrderByTradeDateDesc("0050")
                            .take(252)
                            .collectList()
                            .flatMap(list -> {
                                if (list.isEmpty()) {
                                    return quoteRepository.findByTickerOrderByTradeDateDesc("^TWII")
                                            .take(252)
                                            .collectList();
                                }
                                return Mono.just(list);
                            })
                            .defaultIfEmpty(Collections.emptyList());

                    Mono<Optional<MarketDailyQuote>> vixQuoteMono = quoteRepository.findByTickerOrderByTradeDateDesc("^VIX")
                            .take(1)
                            .next()
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty());

                    return Mono.zip(coreQuotesMono, vixQuoteMono)
                            .map(tuple -> calculateAssessment(snapshot, tuple.getT1(), tuple.getT2().orElse(null)));
                })
                .doOnError(e -> log.error("Failed to evaluate macro yield regime: {}", e.getMessage(), e));
    }

    public MacroRegimeAssessment calculateAssessment(MacroYieldSnapshot snapshot) {
        return calculateAssessment(snapshot, null, null);
    }

    public MacroRegimeAssessment calculateAssessment(MacroYieldSnapshot snapshot,
                                                     List<MarketDailyQuote> coreQuotes,
                                                     MarketDailyQuote vixQuote) {
        if (snapshot == null || snapshot.getUsCorporateBondEffectiveYield() == null) {
            return null;
        }
        BigDecimal yield = snapshot.getUsCorporateBondEffectiveYield();
        double yieldValue = yield.doubleValue();

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

        // Determine Crisis Level:
        // 1. Drawdown on 52-week high of core benchmark
        double drawdown = 0.0;
        if (coreQuotes != null && !coreQuotes.isEmpty()) {
            double currentPrice = coreQuotes.get(0).getClosePrice() != null ? coreQuotes.get(0).getClosePrice().doubleValue() : 0.0;
            double maxPrice = currentPrice;
            for (MarketDailyQuote q : coreQuotes) {
                if (q.getHighPrice() != null && q.getHighPrice().doubleValue() > maxPrice) {
                    maxPrice = q.getHighPrice().doubleValue();
                } else if (q.getClosePrice() != null && q.getClosePrice().doubleValue() > maxPrice) {
                    maxPrice = q.getClosePrice().doubleValue();
                }
            }
            if (maxPrice > 0) {
                drawdown = (currentPrice - maxPrice) / maxPrice;
            }
        }

        double vixValue = (vixQuote != null && vixQuote.getClosePrice() != null)
                ? vixQuote.getClosePrice().doubleValue()
                : 0.0;

        BigDecimal spread = snapshot.getYieldSpread10yMinus2y();

        CrisisLevel crisisLevel = CrisisLevel.NORMAL;
        if (drawdown <= -0.30) {
            crisisLevel = CrisisLevel.CRISIS_LEVEL_2;
            summary += " 【黑天鵝救災 CRISIS_LEVEL_2】核心大盤回撤達 " + String.format("%.1f%%", drawdown * 100) + "，啟動終極救災條款，強制建議自防禦債券套現 50% 抄底核心大盤。";
        } else if (drawdown <= -0.15 && vixValue >= 30.0) {
            crisisLevel = CrisisLevel.CRISIS_LEVEL_1;
            summary += " 【恐慌抄底 CRISIS_LEVEL_1】核心大盤回撤達 " + String.format("%.1f%%", drawdown * 100) + " 且 VIX 達 " + String.format("%.1f", vixValue) + "，啟動一級逆向單筆抄底訊號！";
        } else if (drawdown <= -0.10 || (spread != null && spread.doubleValue() < -0.50)) {
            crisisLevel = CrisisLevel.CORRECTION;
            if (drawdown <= -0.10) {
                summary += " 【修正期 CORRECTION】核心大盤回撤達 " + String.format("%.1f%%", drawdown * 100) + "，保持紀律靜默定期定額扣款。";
            } else {
                summary += " 【殖利率倒掛警示】10Y-2Y 殖利率倒掛達 " + spread + "%，注意宏觀修正風險。";
            }
        }

        return new MacroRegimeAssessment(state, equityRatio, bondRatio, yieldValue, summary, crisisLevel);
    }
}
