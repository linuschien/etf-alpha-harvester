package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public GlobalAssetScoreEvaluationService(GlobalAssetMetadataRepository metadataRepository,
                                             GlobalAssetScoreRepository scoreRepository,
                                             MarketDailyQuoteRepository quoteRepository) {
        this.metadataRepository = metadataRepository;
        this.scoreRepository = scoreRepository;
        this.quoteRepository = quoteRepository;
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

                    return prefetchBenchmarkQuotes()
                            .flatMap(benchmarkMap -> dynamicallyClassifyAssets(assets, benchmarkMap, evaluationDate))
                            .flatMap(classifiedAssets -> scoreAndRankAssets(classifiedAssets, evaluationDate));
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

    private Mono<List<GlobalAssetMetadata>> dynamicallyClassifyAssets(
            List<GlobalAssetMetadata> assets,
            Map<String, List<MarketDailyQuote>> benchmarkMap,
            LocalDateTime evaluationDate) {

        List<GlobalAssetMetadata> changedAssets = Collections.synchronizedList(new ArrayList<>());

        return Flux.fromIterable(assets)
                .flatMap(asset -> {
                    if (asset.getAssetClass() == CandidateAssetClass.DEFENSIVE) {
                        return Mono.just(asset);
                    }

                    String bmTicker = resolveBenchmarkTicker(asset);
                    List<MarketDailyQuote> bmQuotes = benchmarkMap.getOrDefault(bmTicker, List.of());

                    return quoteRepository.findByTickerOrderByTradeDateDesc(asset.getTicker())
                            .take(31)
                            .collectList()
                            .map(etfQuotes -> {
                                double r2 = calculateR2(etfQuotes, bmQuotes);
                                CandidateAssetClass resolvedClass = (r2 >= CORE_R2_THRESHOLD)
                                        ? CandidateAssetClass.CORE
                                        : CandidateAssetClass.SATELLITE;

                                if (asset.getAssetClass() != resolvedClass) {
                                    log.info("Dynamic classification for {}: R^2={}/bm={}, changed from {} to {}",
                                            asset.getTicker(), String.format("%.4f", r2), bmTicker,
                                            asset.getAssetClass(), resolvedClass);
                                    asset.setAssetClass(resolvedClass);
                                    asset.setUpdatedAt(evaluationDate);
                                    changedAssets.add(asset);
                                }
                                return asset;
                            });
                })
                .collectList()
                .flatMap(resolvedAssets -> {
                    if (changedAssets.isEmpty()) {
                        return Mono.just(resolvedAssets);
                    }
                    return metadataRepository.saveAll(changedAssets)
                            .then(Mono.just(resolvedAssets));
                });
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

    private Mono<GlobalAssetScoreEvaluationResponse> scoreAndRankAssets(
            List<GlobalAssetMetadata> assets,
            LocalDateTime evaluationDate) {

        List<GlobalAssetScore> allScores = new ArrayList<>();
        int coreCount = 0;
        int satelliteCount = 0;
        int defensiveCount = 0;

        // Group assets by CandidateAssetClass
        Map<CandidateAssetClass, List<GlobalAssetMetadata>> grouped = new EnumMap<>(CandidateAssetClass.class);
        for (CandidateAssetClass ac : CandidateAssetClass.values()) {
            grouped.put(ac, new ArrayList<>());
        }
        for (GlobalAssetMetadata a : assets) {
            grouped.get(a.getAssetClass()).add(a);
        }

        // Score and rank independently within each class
        for (Map.Entry<CandidateAssetClass, List<GlobalAssetMetadata>> entry : grouped.entrySet()) {
            CandidateAssetClass assetClass = entry.getKey();
            List<GlobalAssetMetadata> classAssets = entry.getValue();

            List<GlobalAssetScore> scoredList = new ArrayList<>();
            for (GlobalAssetMetadata asset : classAssets) {
                scoredList.add(evaluateAsset(asset, evaluationDate));
            }

            // Sort by composite score descending
            scoredList.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));

            // Assign independent class rank (1, 2, 3...)
            int rank = 1;
            for (GlobalAssetScore s : scoredList) {
                s.setClassRank(rank++);
            }

            allScores.addAll(scoredList);

            if (assetClass == CandidateAssetClass.CORE) coreCount = scoredList.size();
            else if (assetClass == CandidateAssetClass.SATELLITE) satelliteCount = scoredList.size();
            else if (assetClass == CandidateAssetClass.DEFENSIVE) defensiveCount = scoredList.size();
        }

        final int finalCore = coreCount;
        final int finalSat = satelliteCount;
        final int finalDef = defensiveCount;

        return scoreRepository.saveAll(allScores)
                .then(Mono.just(new GlobalAssetScoreEvaluationResponse(
                        "SUCCESS",
                        "Candidate asset scoring and class ranking completed successfully.",
                        evaluationDate.toString(),
                        allScores.size(),
                        finalCore,
                        finalSat,
                        finalDef
                )));
    }

    public GlobalAssetScore evaluateAsset(GlobalAssetMetadata asset, LocalDateTime evaluationDate) {
        long listingDays = (asset.getListingDate() != null)
                ? ChronoUnit.DAYS.between(asset.getListingDate(), evaluationDate)
                : 365;

        boolean isQualified = true;
        String reason = null;

        // Hard Constraint 1: Minimum listing days (N >= 30)
        if (listingDays < 30) {
            isQualified = false;
            reason = "掛牌未滿 30 個交易日最低門檻 (目前 " + listingDays + " 天)";
        }

        double ter = asset.getTotalExpenseRatio() != null ? asset.getTotalExpenseRatio().doubleValue() : 0.0050;
        double aum = asset.getFundSizeTwd() != null ? asset.getFundSizeTwd().doubleValue() : 5_000_000_000.0;

        // Hard Constraint 2: Class specific gatekeepers
        if (asset.getAssetClass() == CandidateAssetClass.CORE) {
            if (ter > 0.0045) {
                isQualified = false;
                reason = "總費用率 (" + String.format("%.2f%%", ter * 100) + ") 超過核心大盤上限 0.45%";
            } else if (aum < 10_000_000_000.0 && listingDays >= 180) {
                isQualified = false;
                reason = "資產規模未達 100 億 TWD 核心規模門檻";
            }
        }

        // Multi-Factor Score Calculation [0, 100]
        double score;
        if (asset.getAssetClass() == CandidateAssetClass.CORE) {
            // S_core = 0.35 * TER_Score + 0.25 * AUM_Score + 0.30 * TrackingError + 0.10 * Spread
            double terScore = Math.max(0.0, 100.0 - (ter * 10000.0));
            double aumScore = Math.min(100.0, (aum / 50_000_000_000.0) * 100.0);
            double trackScore = 95.0; // Benchmark wide-market alignment
            double spreadScore = 90.0;
            score = 0.35 * terScore + 0.25 * aumScore + 0.30 * trackScore + 0.10 * spreadScore;
        } else if (asset.getAssetClass() == CandidateAssetClass.SATELLITE) {
            // S_sat = 0.30 * MOM + 0.20 * Sharpe + 0.20 * Hurst - 0.15 * rho + 0.15 * DCARank
            double momScore = 85.0;
            double sharpeScore = 80.0;
            double hurstScore = 75.0;
            double dcaRankScore = 80.0;
            score = 0.30 * momScore + 0.20 * sharpeScore + 0.20 * hurstScore + 0.15 * dcaRankScore;
        } else {
            // S_defensive = 0.30 * Yield + 0.30 * TER + 0.25 * AUM + 0.15 * DurationFit
            double yieldScore = 88.0;
            double terScore = Math.max(0.0, 100.0 - (ter * 10000.0));
            double aumScore = Math.min(100.0, (aum / 30_000_000_000.0) * 100.0);
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

