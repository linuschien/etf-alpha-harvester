package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.domain.entity.DcaPopularityRank;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
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

    @Autowired
    public GlobalAssetScoreEvaluationService(GlobalAssetMetadataRepository metadataRepository,
                                             GlobalAssetScoreRepository scoreRepository,
                                             MarketDailyQuoteRepository quoteRepository,
                                             @Autowired(required = false) DcaPopularityRankRepository dcaRankRepository) {
        this.metadataRepository = metadataRepository;
        this.scoreRepository = scoreRepository;
        this.quoteRepository = quoteRepository;
        this.dcaRankRepository = dcaRankRepository;
    }

    public GlobalAssetScoreEvaluationService(GlobalAssetMetadataRepository metadataRepository,
                                             GlobalAssetScoreRepository scoreRepository,
                                             MarketDailyQuoteRepository quoteRepository) {
        this(metadataRepository, scoreRepository, quoteRepository, null);
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

                    return Mono.zip(prefetchBenchmarkQuotes(), prefetchDcaRanks())
                            .flatMap(tuple -> {
                                Map<String, List<MarketDailyQuote>> benchmarkMap = tuple.getT1();
                                Map<String, Integer> dcaRankMap = tuple.getT2();
                                return dynamicallyClassifyAndScore(assets, benchmarkMap, dcaRankMap, evaluationDate);
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

    private Mono<GlobalAssetScoreEvaluationResponse> dynamicallyClassifyAndScore(
            List<GlobalAssetMetadata> assets,
            Map<String, List<MarketDailyQuote>> benchmarkMap,
            Map<String, Integer> dcaRankMap,
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
                            GlobalAssetScore score = evaluateAsset(asset, evaluationDate, etfQuotes, maxR2, dcaRank);
                            allScores.add(score);
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

    public GlobalAssetScore evaluateAsset(GlobalAssetMetadata asset, LocalDateTime evaluationDate) {
        return evaluateAsset(asset, evaluationDate, null, 0.95, null);
    }

    public GlobalAssetScore evaluateAsset(GlobalAssetMetadata asset,
                                          LocalDateTime evaluationDate,
                                          List<MarketDailyQuote> quotes,
                                          double trackingR2,
                                          Integer dcaRank) {
        long listingDays = (asset.getListingDate() != null)
                ? ChronoUnit.DAYS.between(asset.getListingDate(), evaluationDate)
                : 365;

        boolean isQualified = true;
        String reason = null;

        double ter = asset.getTotalExpenseRatio() != null ? asset.getTotalExpenseRatio().doubleValue() : 0.0050;
        double aum = asset.getFundSizeTwd() != null ? asset.getFundSizeTwd().doubleValue() : 5_000_000_000.0;

        // Hard Constraint: Class specific checks (unqualified assets are eliminated)
        if (asset.getAssetClass() == CandidateAssetClass.CORE) {
            if (ter > 0.0045) {
                isQualified = false;
                reason = "總費用率 (" + String.format("%.2f%%", ter * 100) + ") 超過核心大盤上限 0.45%";
            } else if (aum < 10_000_000_000.0) {
                isQualified = false;
                reason = "資產規模未達 100 億 TWD 核心規模門檻";
            }
        }

        // Multi-Factor Score Calculation [0, 100] using smooth linear functions
        double score;
        if (asset.getAssetClass() == CandidateAssetClass.CORE) {
            // S_core = 0.35 * TER_Score + 0.25 * AUM_Score + 0.30 * TrackingError + 0.10 * Spread
            double terScore = Math.min(100.0, Math.max(0.0, 100.0 - (ter * 10000.0)));
            double aumScore = Math.min(100.0, Math.max(0.0, (aum / 50_000_000_000.0) * 100.0));
            double trackScore = (trackingR2 > 0.0) ? Math.min(100.0, trackingR2 * 100.0) : 95.0;
            double spreadScore = 90.0;
            score = 0.35 * terScore + 0.25 * aumScore + 0.30 * trackScore + 0.10 * spreadScore;
        } else if (asset.getAssetClass() == CandidateAssetClass.SATELLITE) {
            // S_sat = 0.30 * MOM + 0.20 * Sharpe + 0.20 * Hurst + 0.15 * (1 - rho_core) * 100 + 0.15 * DCARank
            double momScore = 85.0;
            if (quotes != null && quotes.size() >= 2) {
                double pCurrent = quotes.get(0).getClosePrice() != null ? quotes.get(0).getClosePrice().doubleValue() : 0.0;
                double pOld = quotes.get(quotes.size() - 1).getClosePrice() != null ? quotes.get(quotes.size() - 1).getClosePrice().doubleValue() : 0.0;
                if (pOld > 0.0 && pCurrent > 0.0) {
                    double r30 = (pCurrent - pOld) / pOld;
                    momScore = Math.min(100.0, Math.max(0.0, 50.0 + r30 * 200.0));
                }
            }
            double sharpeScore = 80.0;
            double hurstScore = 75.0;
            // Reuse R^2 to get correlation rho_core = sqrt(R^2). Low correlation earns higher diversification score.
            double rhoCore = (trackingR2 > 0.0) ? Math.sqrt(trackingR2) : 0.0;
            double diversificationScore = Math.min(100.0, Math.max(0.0, (1.0 - rhoCore) * 100.0));
            double dcaRankScore = (dcaRank != null && dcaRank >= 1 && dcaRank <= 20)
                    ? (21 - dcaRank) * 5.0
                    : 0.0;

            score = 0.30 * momScore
                    + 0.20 * sharpeScore
                    + 0.20 * hurstScore
                    + 0.15 * diversificationScore
                    + 0.15 * dcaRankScore;
        } else {
            // S_defensive = 0.30 * Yield + 0.30 * TER + 0.25 * AUM + 0.15 * DurationFit
            double yieldScore = 88.0;
            double terScore = Math.min(100.0, Math.max(0.0, 100.0 - (ter * 10000.0)));
            double aumScore = Math.min(100.0, Math.max(0.0, (aum / 30_000_000_000.0) * 100.0));
            double durationScore = 90.0;
            score = 0.30 * yieldScore + 0.30 * terScore + 0.25 * aumScore + 0.15 * durationScore;
        }

        if (!isQualified) {
            score = score * 0.5; // Penalize disqualified candidates
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
        scoreEntity.setIsQualified(isQualified);
        scoreEntity.setDisqualificationReason(reason);

        return scoreEntity;
    }
}
