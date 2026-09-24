package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.MacroYieldSnapshotRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.GatekeeperReport;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DataCompletenessGatekeeperService {

    private static final Logger log = LoggerFactory.getLogger(DataCompletenessGatekeeperService.class);

    private final GlobalAssetMetadataRepository metadataRepository;
    private final MarketDailyQuoteRepository quoteRepository;
    private final MacroYieldSnapshotRepository macroYieldRepository;

    public DataCompletenessGatekeeperService(GlobalAssetMetadataRepository metadataRepository,
                                             MarketDailyQuoteRepository quoteRepository,
                                             MacroYieldSnapshotRepository macroYieldRepository) {
        this.metadataRepository = metadataRepository;
        this.quoteRepository = quoteRepository;
        this.macroYieldRepository = macroYieldRepository;
    }

    public Mono<GatekeeperReport> checkCompleteness() {
        LocalDateTime now = LocalDateTime.now();
        List<String> violations = Collections.synchronizedList(new ArrayList<>());

        // Check 1: Macro yield snapshot exists and is in range [0.01, 0.20] (1% to 20%)
        Mono<MacroYieldSnapshot> macroSnapshotMono = macroYieldRepository.findTopByOrderByRecordDateDesc();
        Mono<Boolean> macroCheckMono = (macroSnapshotMono != null ? macroSnapshotMono : Mono.<MacroYieldSnapshot>empty())
                .map(snapshot -> {
                    BigDecimal yield = snapshot.getUsCorporateBondEffectiveYield();
                    if (yield == null) {
                        violations.add("美國投資級公司債殖利率缺失");
                        return false;
                    }
                    double y = yield.doubleValue();
                    double normalizedYield = (y > 1.0) ? y / 100.0 : y;
                    if (normalizedYield < 0.01 || normalizedYield > 0.20) {
                        violations.add("公司債殖利率異常偏離合理區間 [1%, 20%]: " + y);
                        return false;
                    }
                    return true;
                })
                .defaultIfEmpty(false)
                .doOnNext(valid -> {
                    if (!valid && violations.stream().noneMatch(v -> v.contains("殖利率"))) {
                        violations.add("未找到最新宏觀殖利率快照 (MacroYieldSnapshot)");
                    }
                });

        // Check 2: Core benchmark quote (0050 or ^TWII) exists and has closePrice > 0
        Mono<MarketDailyQuote> quote0050 = quoteRepository.findFirstByTickerOrderByTradeDateDesc("0050");
        Mono<MarketDailyQuote> benchmarkQuoteMono = (quote0050 != null ? quote0050 : Mono.<MarketDailyQuote>empty())
                .switchIfEmpty(Mono.defer(() -> {
                    Mono<MarketDailyQuote> quoteTwii = quoteRepository.findFirstByTickerOrderByTradeDateDesc("^TWII");
                    return quoteTwii != null ? quoteTwii : Mono.empty();
                }));

        Mono<Boolean> benchmarkCheckMono = benchmarkQuoteMono
                .map(quote -> {
                    if (quote.getClosePrice() == null || quote.getClosePrice().compareTo(BigDecimal.ZERO) <= 0) {
                        violations.add("基準標的 (" + quote.getTicker() + ") 最新收盤價異常或非正數");
                        return false;
                    }
                    return true;
                })
                .defaultIfEmpty(false)
                .doOnNext(valid -> {
                    if (!valid && violations.stream().noneMatch(v -> v.contains("缺失最新交易報價") || v.contains("基準標的"))) {
                        violations.add("基準標的 (0050 / ^TWII) 缺失最新交易報價");
                    }
                });

        Flux<GlobalAssetMetadata> allMetadata = metadataRepository.findAll();
        Mono<Integer> candidatesCountMono = (allMetadata != null ? allMetadata : Flux.<GlobalAssetMetadata>empty())
                .count()
                .map(Long::intValue)
                .defaultIfEmpty(0);

        return Mono.zip(macroCheckMono, benchmarkCheckMono, candidatesCountMono)
                .map(tuple -> {
                    boolean macroValid = tuple.getT1();
                    boolean benchmarkValid = tuple.getT2();
                    int checkedCount = tuple.getT3();
                    boolean pass = macroValid && benchmarkValid && violations.isEmpty();
                    String status = pass ? "PASS" : "HALT";
                    String message = pass
                            ? "數據齊備性檢查通過，允許下游量化引擎運算。"
                            : "市場數據採集未齊全，量化引擎已安全暫停，請稍後重試或檢查數據源。異常項目數: " + violations.size();

                    if (!pass) {
                        log.warn("DataCompletenessGatekeeper HALT: {}", violations);
                    } else {
                        log.info("DataCompletenessGatekeeper PASS: verified macro yields and core benchmark. Total universe: {} assets", checkedCount);
                    }

                    return new GatekeeperReport(status, message, now, checkedCount, macroValid, new ArrayList<>(violations));
                });
    }
}
