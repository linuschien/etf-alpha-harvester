package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.DividendAnnouncementRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.domain.entity.DcaPopularityRank;
import com.alphaharvester.domain.entity.DividendAnnouncement;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GlobalAssetScoreEvaluationService implements GlobalAssetScoreEvaluationUseCase {

    private static final Logger log = LoggerFactory.getLogger(GlobalAssetScoreEvaluationService.class);
    public static final double CORE_R2_THRESHOLD = 0.95;

    private final GlobalAssetMetadataRepository metadataRepository;
    private final GlobalAssetScoreRepository scoreRepository;
    private final MarketDailyQuoteRepository quoteRepository;
    private final DcaPopularityRankRepository dcaRankRepository;
    private final DividendAnnouncementRepository dividendRepository;

    @Autowired
    public GlobalAssetScoreEvaluationService(GlobalAssetMetadataRepository metadataRepository,
                                             GlobalAssetScoreRepository scoreRepository,
                                             MarketDailyQuoteRepository quoteRepository,
                                             @Autowired(required = false) DcaPopularityRankRepository dcaRankRepository,
                                             @Autowired(required = false) DividendAnnouncementRepository dividendRepository) {
        this.metadataRepository = metadataRepository;
        this.scoreRepository = scoreRepository;
        this.quoteRepository = quoteRepository;
        this.dcaRankRepository = dcaRankRepository;
        this.dividendRepository = dividendRepository;
    }

    public GlobalAssetScoreEvaluationService(GlobalAssetMetadataRepository metadataRepository,
                                             GlobalAssetScoreRepository scoreRepository,
                                             MarketDailyQuoteRepository quoteRepository,
                                             DcaPopularityRankRepository dcaRankRepository) {
        this(metadataRepository, scoreRepository, quoteRepository, dcaRankRepository, null);
    }

    public GlobalAssetScoreEvaluationService(GlobalAssetMetadataRepository metadataRepository,
                                             GlobalAssetScoreRepository scoreRepository,
                                             MarketDailyQuoteRepository quoteRepository) {
        this(metadataRepository, scoreRepository, quoteRepository, null, null);
    }

    @Override
    @Transactional
    public Mono<GlobalAssetScoreEvaluationResponse> evaluateGlobalAssetScores() {
        LocalDateTime evaluationDate = LocalDateTime.now();
        log.info("Starting global candidate multi-factor evaluation at {}", evaluationDate);

        return metadataRepository.findAll()
                .collectList()
                .flatMap(assets -> {
                    if (assets.isEmpty()) {
                        log.warn("No GlobalAssetMetadata records found to evaluate.");
                        return Mono.just(new GlobalAssetScoreEvaluationResponse(
                                "SUCCESS",
                                "No candidate assets found for evaluation.",
                                evaluationDate.toString(),
                                0, 0, 0, 0
                        ));
                    }

                    return Mono.zip(prefetchBenchmarkQuotes(), prefetchDcaRanks(), prefetchDividends(evaluationDate))
                            .flatMap(tuple -> {
                                Map<String, List<MarketDailyQuote>> benchmarkMap = tuple.getT1();
                                Map<String, Integer> dcaRankMap = tuple.getT2();
                                Map<String, List<DividendAnnouncement>> dividendMap = tuple.getT3();
                                return dynamicallyClassifyAndScore(assets, benchmarkMap, dcaRankMap, dividendMap, evaluationDate);
                            });
                })
                .doOnError(e -> log.error("Failed during candidate multi-factor evaluation: {}", e.getMessage(), e));
    }

    private Mono<Map<String, List<MarketDailyQuote>>> prefetchBenchmarkQuotes() {
        return Flux.just("^TWII", "^GSPC", "^NDX")
                .flatMap(ticker -> quoteRepository.findByTickerOrderByTradeDateDesc(ticker)
                        .take(31)
                        .collectList()
                        .map(quotes -> Map.entry(ticker, quotes)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<Map<String, Integer>> prefetchDcaRanks() {
        if (dcaRankRepository == null) {
            return Mono.just(Collections.emptyMap());
        }
        return dcaRankRepository.findAll()
                .collectList()
                .map(list -> {
                    Map<String, Integer> map = new HashMap<>();
                    for (DcaPopularityRank rank : list) {
                        if (rank.getTicker() != null && rank.getRankPosition() != null) {
                            map.putIfAbsent(rank.getTicker(), rank.getRankPosition());
                        }
                    }
                    return map;
                })
                .defaultIfEmpty(Collections.emptyMap());
    }

    private Mono<Map<String, List<DividendAnnouncement>>> prefetchDividends(LocalDateTime evaluationDate) {
        if (dividendRepository == null) {
            return Mono.just(Collections.emptyMap());
        }
        LocalDateTime oneYearAgo = evaluationDate.minusDays(365);
        return dividendRepository.findByExDateBetweenOrderByExDateAsc(oneYearAgo, evaluationDate.plusDays(1))
                .collectList()
                .map(list -> {
                    Map<String, List<DividendAnnouncement>> map = new HashMap<>();
                    for (DividendAnnouncement div : list) {
                        if (div.getTicker() != null) {
                            map.computeIfAbsent(div.getTicker(), k -> new ArrayList<>()).add(div);
                        }
                    }
                    return map;
                })
                .defaultIfEmpty(Collections.emptyMap());
    }

    private Mono<GlobalAssetScoreEvaluationResponse> dynamicallyClassifyAndScore(
            List<GlobalAssetMetadata> assets,
            Map<String, List<MarketDailyQuote>> benchmarkMap,
            Map<String, Integer> dcaRankMap,
            Map<String, List<DividendAnnouncement>> dividendMap,
            LocalDateTime evaluationDate) {

        List<GlobalAssetMetadata> changedAssets = Collections.synchronizedList(new ArrayList<>());
        List<GlobalAssetScore> allScores = Collections.synchronizedList(new ArrayList<>());

        return Flux.fromIterable(assets)
                .flatMap(asset -> {
                    Flux<MarketDailyQuote> quotesFlux = (quoteRepository != null)
                            ? quoteRepository.findByTickerOrderByTradeDateDesc(asset.getTicker())
                            : null;
                    if (quotesFlux == null) {
                        quotesFlux = Flux.empty();
                    }
                    return quotesFlux.take(31)
                            .collectList()
                            .map(etfQuotes -> {
                            double r2Twii = calculateR2(etfQuotes, benchmarkMap.getOrDefault("^TWII", List.of()));
                            double r2Gspc = calculateR2(etfQuotes, benchmarkMap.getOrDefault("^GSPC", List.of()));
                            double r2Ndx  = calculateR2(etfQuotes, benchmarkMap.getOrDefault("^NDX", List.of()));
                            double maxR2  = Math.max(r2Twii, Math.max(r2Gspc, r2Ndx));

                            if (asset.getAssetClass() != CandidateAssetClass.DEFENSIVE) {
                                CandidateAssetClass resolvedClass = (maxR2 >= CORE_R2_THRESHOLD)
                                         ? CandidateAssetClass.CORE
                                        : CandidateAssetClass.SATELLITE;

                                if (asset.getAssetClass() != resolvedClass) {
                                    log.info("Dynamic classification for {}: max(R^2)={} across 3 benchmarks, changed from {} to {}",
                                            asset.getTicker(), String.format("%.4f", maxR2), asset.getAssetClass(), resolvedClass);
                                    asset.setAssetClass(resolvedClass);
                                    asset.setUpdatedAt(evaluationDate);
                                    changedAssets.add(asset);
                                }
                            }

                            Integer dcaRank = dcaRankMap.get(asset.getTicker());
                            List<DividendAnnouncement> divs = dividendMap.get(asset.getTicker());

                            DistributionFrequency dynamicFreq = deriveDistributionFrequency(divs);
                            if (asset.getDistributionFrequency() != dynamicFreq) {
                                log.info("Dynamic distribution frequency for {}: derived {} from {} dividend announcements in past year (was {})",
                                        asset.getTicker(), dynamicFreq, divs != null ? divs.size() : 0, asset.getDistributionFrequency());
                                asset.setDistributionFrequency(dynamicFreq);
                                asset.setUpdatedAt(evaluationDate);
                                if (!changedAssets.contains(asset)) {
                                    changedAssets.add(asset);
                                }
                            }

                            GlobalAssetScore score = evaluateAsset(asset, evaluationDate, etfQuotes, maxR2, dcaRank, divs);
                            if (score != null) {
                                allScores.add(score);
                            }
                            return asset;
                        });
                })
                .collectList()
                .flatMap(resolvedAssets -> {
                    Mono<Void> saveMetadata = changedAssets.isEmpty()
                            ? Mono.empty()
                            : metadataRepository.saveAll(changedAssets).then();

                    return saveMetadata.then(rankAndSaveScores(allScores, evaluationDate));
                });
    }

    private Mono<GlobalAssetScoreEvaluationResponse> rankAndSaveScores(
            List<GlobalAssetScore> allScores,
            LocalDateTime evaluationDate) {

        int coreCount = 0;
        int satelliteCount = 0;
        int defensiveCount = 0;

        Map<CandidateAssetClass, List<GlobalAssetScore>> grouped = new EnumMap<>(CandidateAssetClass.class);
        for (CandidateAssetClass ac : CandidateAssetClass.values()) {
            grouped.put(ac, new ArrayList<>());
        }
        for (GlobalAssetScore s : allScores) {
            grouped.get(s.getAssetClass()).add(s);
        }

        List<GlobalAssetScore> finalRankedScores = new ArrayList<>();
        for (Map.Entry<CandidateAssetClass, List<GlobalAssetScore>> entry : grouped.entrySet()) {
            CandidateAssetClass ac = entry.getKey();
            List<GlobalAssetScore> list = entry.getValue();

            list.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));
            int rank = 1;
            for (GlobalAssetScore s : list) {
                s.setClassRank(rank++);
            }
            finalRankedScores.addAll(list);

            if (ac == CandidateAssetClass.CORE) coreCount = list.size();
            else if (ac == CandidateAssetClass.SATELLITE) satelliteCount = list.size();
            else if (ac == CandidateAssetClass.DEFENSIVE) defensiveCount = list.size();
        }

        final int finalCore = coreCount;
        final int finalSat = satelliteCount;
        final int finalDef = defensiveCount;

        return scoreRepository.saveAll(finalRankedScores)
                .then(Mono.just(new GlobalAssetScoreEvaluationResponse(
                        "SUCCESS",
                        "Candidate asset scoring and class ranking completed successfully.",
                        evaluationDate.toString(),
                        finalRankedScores.size(),
                        finalCore,
                        finalSat,
                        finalDef
                )));
    }

    public String resolveBenchmarkTicker(GlobalAssetMetadata asset) {
        String name = asset.getName() != null ? asset.getName().toUpperCase() : "";
        String idx = asset.getUnderlyingIndex() != null ? asset.getUnderlyingIndex().toUpperCase() : "";
        if (name.contains("S&P") || name.contains("標普") || name.contains("500") || idx.contains("S&P") || idx.contains("500")) {
            return "^GSPC";
        }
        if (name.contains("NASDAQ") || name.contains("那斯達克") || idx.contains("NASDAQ")) {
            return "^NDX";
        }
        return "^TWII";
    }

    public double calculateR2(List<MarketDailyQuote> etfQuotes, List<MarketDailyQuote> bmQuotes) {
        if (etfQuotes == null || bmQuotes == null || etfQuotes.size() < 2 || bmQuotes.size() < 2) {
            return 0.0;
        }

        Map<LocalDate, Double> etfPriceMap = new TreeMap<>();
        for (MarketDailyQuote q : etfQuotes) {
            if (q != null && q.getTradeDate() != null && q.getClosePrice() != null) {
                etfPriceMap.putIfAbsent(q.getTradeDate().toLocalDate(), q.getClosePrice().doubleValue());
            }
        }

        Map<LocalDate, Double> bmPriceMap = new TreeMap<>();
        for (MarketDailyQuote q : bmQuotes) {
            if (q != null && q.getTradeDate() != null && q.getClosePrice() != null) {
                bmPriceMap.putIfAbsent(q.getTradeDate().toLocalDate(), q.getClosePrice().doubleValue());
            }
        }

        List<LocalDate> commonDates = new ArrayList<>();
        for (LocalDate date : etfPriceMap.keySet()) {
            if (bmPriceMap.containsKey(date)) {
                commonDates.add(date);
            }
        }
        Collections.sort(commonDates);

        if (commonDates.size() < 3) {
            return 0.0;
        }

        // Limit to recent 31 price points (representing up to 30 trading intervals)
        if (commonDates.size() > 31) {
            commonDates = commonDates.subList(commonDates.size() - 31, commonDates.size());
        }

        int n = commonDates.size() - 1;
        double[] rEtf = new double[n];
        double[] rBm = new double[n];

        double sumEtf = 0.0;
        double sumBm = 0.0;

        for (int i = 0; i < n; i++) {
            LocalDate dPrev = commonDates.get(i);
            LocalDate dCurr = commonDates.get(i + 1);

            double pEtfPrev = etfPriceMap.get(dPrev);
            double pEtfCurr = etfPriceMap.get(dCurr);
            double pBmPrev = bmPriceMap.get(dPrev);
            double pBmCurr = bmPriceMap.get(dCurr);

            if (pEtfPrev <= 0.0 || pBmPrev <= 0.0) {
                return 0.0;
            }

            rEtf[i] = (pEtfCurr - pEtfPrev) / pEtfPrev;
            rBm[i] = (pBmCurr - pBmPrev) / pBmPrev;

            sumEtf += rEtf[i];
            sumBm += rBm[i];
        }

        double meanEtf = sumEtf / n;
        double meanBm = sumBm / n;

        double cov = 0.0;
        double varEtf = 0.0;
        double varBm = 0.0;

        for (int i = 0; i < n; i++) {
            double dEtf = rEtf[i] - meanEtf;
            double dBm = rBm[i] - meanBm;

            cov += dEtf * dBm;
            varEtf += dEtf * dEtf;
            varBm += dBm * dBm;
        }

        if (varEtf <= 1e-12 || varBm <= 1e-12) {
            return 0.0;
        }

        double rho = cov / Math.sqrt(varEtf * varBm);
        return (rho > 0.0) ? Math.min(1.0, rho * rho) : 0.0;
    }

    public static DistributionFrequency deriveDistributionFrequency(List<DividendAnnouncement> dividendsPastYear) {
        if (dividendsPastYear == null || dividendsPastYear.isEmpty()) {
            return DistributionFrequency.NONE;
        }
        long count = dividendsPastYear.stream()
                .filter(d -> d != null && d.getDividendPerShare() != null && d.getDividendPerShare().compareTo(BigDecimal.ZERO) > 0)
                .count();

        if (count >= 10) {
            return DistributionFrequency.MONTHLY;
        } else if (count >= 3) {
            return DistributionFrequency.QUARTERLY;
        } else if (count == 2) {
            return DistributionFrequency.SEMI_ANNUAL;
        } else if (count == 1) {
            return DistributionFrequency.ANNUAL;
        } else {
            return DistributionFrequency.NONE;
        }
    }

    public GlobalAssetScore evaluateAsset(GlobalAssetMetadata asset, LocalDateTime evaluationDate) {
        return evaluateAsset(asset, evaluationDate, null, 0.95, null, null);
    }

    public GlobalAssetScore evaluateAsset(GlobalAssetMetadata asset,
                                          LocalDateTime evaluationDate,
                                          List<MarketDailyQuote> quotes,
                                          double trackingR2,
                                          Integer dcaRank) {
        return evaluateAsset(asset, evaluationDate, quotes, trackingR2, dcaRank, null);
    }

    public GlobalAssetScore evaluateAsset(GlobalAssetMetadata asset,
                                          LocalDateTime evaluationDate,
                                          List<MarketDailyQuote> quotes,
                                          double trackingR2,
                                          Integer dcaRank,
                                          List<DividendAnnouncement> dividends) {
        boolean isQualified = true;
        String reason = null;

        double ter = asset.getTotalExpenseRatio() != null ? asset.getTotalExpenseRatio().doubleValue() : 0.0050;
        double aum = asset.getFundSizeTwd() != null ? asset.getFundSizeTwd().doubleValue() : 5_000_000_000.0;
        String ticker = asset.getTicker() != null ? asset.getTicker().toUpperCase() : "";

        // Hard Constraints: Class-specific checks (unqualified assets are eliminated)
        if (asset.getAssetClass() == CandidateAssetClass.CORE) {
            if (ter > 0.0045) {
                isQualified = false;
                reason = "總費用率 (" + String.format("%.2f%%", ter * 100) + ") 超過核心大盤上限 0.45%";
            } else if (aum < 10_000_000_000.0) {
                isQualified = false;
                reason = "資產規模未達 100 億 TWD 核心規模門檻";
            }
        } else if (asset.getAssetClass() == CandidateAssetClass.SATELLITE) {
            if (aum < 2_000_000_000.0) {
                isQualified = false;
                reason = "資產規模未達 20 億 TWD 衛星規模門檻";
            } else if (quotes == null || quotes.isEmpty()) {
                isQualified = false;
                reason = "無市場成交報價資料，無法驗證 2,000 萬 TWD 衛星流動性門檻";
            } else {
                double totalTurnover = 0.0;
                int quoteCount = 0;
                for (MarketDailyQuote q : quotes) {
                    if (q != null && q.getTradeValueTwd() != null && q.getTradeValueTwd().compareTo(BigDecimal.ZERO) > 0) {
                        totalTurnover += q.getTradeValueTwd().doubleValue();
                        quoteCount++;
                    }
                }
                double avgTurnover = quoteCount > 0 ? totalTurnover / quoteCount : 0.0;
                if (quoteCount == 0 || avgTurnover < 20_000_000.0) {
                    isQualified = false;
                    reason = "滾動日均成交金額未達 2,000 萬 TWD 衛星流動性門檻 (當前: " + String.format("%.2f 萬", avgTurnover / 10000.0) + ")";
                }
            }
        } else if (asset.getAssetClass() == CandidateAssetClass.DEFENSIVE) {
            if (aum < 5_000_000_000.0) {
                isQualified = false;
                reason = "資產規模未達 50 億 TWD 防禦資產規模門檻";
            } else if (ticker.endsWith("L") || ticker.endsWith("R")) {
                isQualified = false;
                reason = "防禦資產嚴禁槓桿或反向型標的 (" + ticker + ")";
            } else if (quotes == null || quotes.isEmpty()) {
                isQualified = false;
                reason = "無市場成交報價資料，無法取得最新收盤市價與計算實質殖利率";
            }
        }

        // If disqualified, return null (candidate eliminated from investable universe)
        if (!isQualified) {
            log.info("Asset {} eliminated by hard constraints: {}", asset.getTicker(), reason);
            return null;
        }

        // Multi-Factor Score Calculation [0, 100] using 100% objective real market data
        double dcaRankScore = (dcaRank != null && dcaRank >= 1 && dcaRank <= 20)
                ? (21 - dcaRank) * 5.0
                : 0.0;

        double score;
        if (asset.getAssetClass() == CandidateAssetClass.CORE) {
            // S_core = 0.30 * TER_Score + 0.25 * AUM_Score + 0.25 * TrackingError + 0.20 * DCARank
            double terScore = Math.min(100.0, Math.max(0.0, 100.0 - (ter * 10000.0)));
            double aumScore = Math.min(100.0, Math.max(0.0, (aum / 50_000_000_000.0) * 100.0));
            double trackScore = (trackingR2 > 0.0) ? Math.min(100.0, trackingR2 * 100.0) : 0.0;
            score = 0.30 * terScore + 0.25 * aumScore + 0.25 * trackScore + 0.20 * dcaRankScore;
        } else if (asset.getAssetClass() == CandidateAssetClass.SATELLITE) {
            // S_sat = 0.40 * MOM + 0.30 * (1 - rho_core) * 100 + 0.30 * DCARank
            double momScore = 50.0;
            if (quotes != null && quotes.size() >= 2) {
                double pCurrent = quotes.get(0).getClosePrice() != null ? quotes.get(0).getClosePrice().doubleValue() : 0.0;
                double pOld = quotes.get(quotes.size() - 1).getClosePrice() != null ? quotes.get(quotes.size() - 1).getClosePrice().doubleValue() : 0.0;
                if (pOld > 0.0 && pCurrent > 0.0) {
                    double r30 = (pCurrent - pOld) / pOld;
                    momScore = Math.min(100.0, Math.max(0.0, 50.0 + r30 * 200.0));
                }
            }
            // Reuse R^2 to get correlation rho_core = sqrt(R^2). Low correlation earns higher diversification score.
            double rhoCore = (trackingR2 > 0.0) ? Math.sqrt(trackingR2) : 0.0;
            double diversificationScore = Math.min(100.0, Math.max(0.0, (1.0 - rhoCore) * 100.0));

            score = 0.40 * momScore
                    + 0.30 * diversificationScore
                    + 0.30 * dcaRankScore;
        } else {
            // S_defensive = 0.40 * Yield + 0.30 * TER + 0.30 * AUM
            double yieldScore = 0.0;
            if (dividends != null && !dividends.isEmpty() && quotes != null && !quotes.isEmpty()) {
                double closePrice = (quotes.get(0).getClosePrice() != null) ? quotes.get(0).getClosePrice().doubleValue() : 0.0;
                if (closePrice > 0.0) {
                    LocalDateTime oneYearAgo = evaluationDate.minusDays(365);
                    double trailingDividend = dividends.stream()
                            .filter(d -> d.getExDate() != null && !d.getExDate().isBefore(oneYearAgo) && !d.getExDate().isAfter(evaluationDate))
                            .filter(d -> d.getDividendPerShare() != null)
                            .mapToDouble(d -> d.getDividendPerShare().doubleValue())
                            .sum();
                    double annualYield = trailingDividend / closePrice;
                    yieldScore = Math.min(100.0, Math.max(0.0, annualYield * 1666.67));
                }
            }
            double terScore = Math.min(100.0, Math.max(0.0, 100.0 - (ter * 10000.0)));
            double aumScore = Math.min(100.0, Math.max(0.0, (aum / 30_000_000_000.0) * 100.0));
            score = 0.40 * yieldScore + 0.30 * terScore + 0.30 * aumScore;
        }

        BigDecimal finalScore = BigDecimal.valueOf(Math.min(100.0, Math.max(0.0, score)))
                .setScale(2, RoundingMode.HALF_UP);

        GlobalAssetScore scoreEntity = new GlobalAssetScore();
        scoreEntity.setAssetId(asset.getId());
        scoreEntity.setTicker(asset.getTicker());
        scoreEntity.setEvaluationDate(evaluationDate);
        scoreEntity.setAssetClass(asset.getAssetClass());
        scoreEntity.setCompositeScore(finalScore);
        scoreEntity.setTotalExpenseRatio(asset.getTotalExpenseRatio());
        scoreEntity.setFundSizeTwd(asset.getFundSizeTwd());

        return scoreEntity;
    }
}
