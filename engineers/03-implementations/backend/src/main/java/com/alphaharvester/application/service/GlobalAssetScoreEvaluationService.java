package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.math.FinancialMetricsCalculator;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.OrthogonalStatus;
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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

import java.util.regex.Pattern;

@Service
public class GlobalAssetScoreEvaluationService implements GlobalAssetScoreEvaluationUseCase {

    private static final Logger log = LoggerFactory.getLogger(GlobalAssetScoreEvaluationService.class);

    public static final String WATERMARK_MONTHLY_TOP_LIST = "MONTHLY_TOP_LIST";
    public static final double CORE_R2_THRESHOLD = 0.90;
    public static final double SATELLITE_VOLATILITY_THRESHOLD = 0.18; // 18%
    public static final double ORTHOGONAL_R2_THRESHOLD = 0.50; // R^2 < 0.50
    public static final Pattern NON_INVESTMENT_GRADE_BOND_PATTERN =
            Pattern.compile("非投資等級|非投等|高收益|High Yield", Pattern.CASE_INSENSITIVE);

    public static boolean isNonInvestmentGradeBond(GlobalAssetMetadata asset) {
        if (asset == null) return false;
        String name = asset.getName() != null ? asset.getName() : "";
        String index = asset.getUnderlyingIndex() != null ? asset.getUnderlyingIndex() : "";
        return NON_INVESTMENT_GRADE_BOND_PATTERN.matcher(name).find()
                || NON_INVESTMENT_GRADE_BOND_PATTERN.matcher(index).find();
    }

    private static final BigDecimal UNIVERSAL_MIN_AUM = new BigDecimal("2000000000"); // 20 億 TWD
    private static final BigDecimal UNIVERSAL_MIN_30D_TURNOVER = new BigDecimal("20000000"); // 2,000 萬 TWD
    private static final int MIN_TRADING_DAYS_365D = 220;

    private final GlobalAssetMetadataRepository metadataRepository;
    private final GlobalAssetScoreRepository scoreRepository;
    private final MarketDailyQuoteRepository quoteRepository;
    private final DcaPopularityRankRepository dcaRankRepository;
    private final DividendAnnouncementRepository dividendRepository;
    private final DataFeedSyncWatermarkRepository watermarkRepository;
    private final GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository;
    private final ExternalMarketDataPort externalMarketDataPort;
    private final CorporateActionRepository corporateActionRepository;

    @Autowired
    public GlobalAssetScoreEvaluationService(GlobalAssetMetadataRepository metadataRepository,
                                             GlobalAssetScoreRepository scoreRepository,
                                             MarketDailyQuoteRepository quoteRepository,
                                             @Autowired(required = false) DcaPopularityRankRepository dcaRankRepository,
                                             @Autowired(required = false) DividendAnnouncementRepository dividendRepository,
                                             @Autowired(required = false) DataFeedSyncWatermarkRepository watermarkRepository,
                                             @Autowired(required = false) GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository,
                                             @Autowired(required = false) ExternalMarketDataPort externalMarketDataPort,
                                             @Autowired(required = false) CorporateActionRepository corporateActionRepository) {
        this.metadataRepository = metadataRepository;
        this.scoreRepository = scoreRepository;
        this.quoteRepository = quoteRepository;
        this.dcaRankRepository = dcaRankRepository;
        this.dividendRepository = dividendRepository;
        this.watermarkRepository = watermarkRepository;
        this.pairwiseMatrixRepository = pairwiseMatrixRepository;
        this.externalMarketDataPort = externalMarketDataPort;
        this.corporateActionRepository = corporateActionRepository;
    }

    @Override
    @Transactional
    public Mono<GlobalAssetScoreEvaluationResponse> evaluateGlobalAssetScores() {
        return evaluateGlobalAssetScores(null, false);
    }

    @Override
    @Transactional
    public Mono<GlobalAssetScoreEvaluationResponse> evaluateGlobalAssetScores(String yearMonth, boolean force) {
        YearMonth targetYm = (yearMonth != null && !yearMonth.isBlank())
                ? YearMonth.parse(yearMonth.trim())
                : YearMonth.from(LocalDate.now());

        LocalDate evalDate = targetYm.atDay(1);
        LocalDateTime evaluationDateTime = evalDate.atStartOfDay();

        LocalDate cutoffDate = evalDate.minusDays(1);
        LocalDateTime cutoffDateTime = cutoffDate.atTime(23, 59, 59);

        LocalDate window365dStart = evalDate.minusYears(1);
        LocalDate window90dStart = evalDate.minusMonths(3);
        LocalDate window30dStart = evalDate.minusMonths(1);

        log.info("Initiating Monthly Top List evaluation for [{}]. Cutoff: {}, 365d: [{} ~ {}], 90d: [{} ~ {}], 30d: [{} ~ {}], Force: {}",
                targetYm, cutoffDate, window365dStart, cutoffDate, window90dStart, cutoffDate, window30dStart, cutoffDate, force);

        if (!force && watermarkRepository != null) {
            return watermarkRepository.findByFeedName(WATERMARK_MONTHLY_TOP_LIST)
                    .flatMap(wm -> {
                        if ("PASS".equalsIgnoreCase(wm.getStatus()) && wm.getLatestRecordDate() != null) {
                            if (wm.getLatestRecordDate().getYear() == evalDate.getYear()
                                    && wm.getLatestRecordDate().getMonthValue() == evalDate.getMonthValue()) {
                                log.info("Monthly Top List for {} has already been generated (Watermark PASS). Skipping calculation.", targetYm);
                                return scoreRepository.findByEvaluationDateOrderByClassRankAsc(evaluationDateTime)
                                        .collectList()
                                        .map(scores -> {
                                            int core = (int) scores.stream().filter(s -> s.getAssetClass() == CandidateAssetClass.CORE).count();
                                            int sat = (int) scores.stream().filter(s -> s.getAssetClass() == CandidateAssetClass.SATELLITE).count();
                                            int def = (int) scores.stream().filter(s -> s.getAssetClass() == CandidateAssetClass.DEFENSIVE).count();
                                            return new GlobalAssetScoreEvaluationResponse(
                                                    "SKIPPED",
                                                    "當月 Top List 已產出，Watermark 斷路跳過重複運算",
                                                    evaluationDateTime.toString(),
                                                    scores.size(), core, sat, def
                                            );
                                        });
                        }
                    }
                    return executePipeline(targetYm, evalDate, evaluationDateTime, cutoffDateTime, window365dStart, window90dStart, window30dStart);
                })
                .switchIfEmpty(executePipeline(targetYm, evalDate, evaluationDateTime, cutoffDateTime, window365dStart, window90dStart, window30dStart));
        }

        return executePipeline(targetYm, evalDate, evaluationDateTime, cutoffDateTime, window365dStart, window90dStart, window30dStart);
    }

    private Mono<GlobalAssetScoreEvaluationResponse> executePipeline(
            YearMonth targetYm, LocalDate evalDate, LocalDateTime evaluationDateTime, LocalDateTime cutoffDateTime,
            LocalDate window365dStart, LocalDate window90dStart, LocalDate window30dStart) {

        LocalDateTime queryStartDateTime = window365dStart.atStartOfDay();

        return metadataRepository.findAll()
                .collectList()
                .flatMap(assets -> {
                    if (assets.isEmpty()) {
                        log.warn("No GlobalAssetMetadata records found to evaluate.");
                        return Mono.just(new GlobalAssetScoreEvaluationResponse(
                                "SUCCESS", "No candidate assets found for evaluation.", evaluationDateTime.toString(), 0, 0, 0, 0
                        ));
                    }

                    return Mono.zip(
                            prefetchBenchmarks(queryStartDateTime, cutoffDateTime),
                            prefetchDcaRanks(),
                            prefetchDividends(window365dStart.atStartOfDay(), cutoffDateTime),
                            prefetchAumMap(),
                            prefetchCorporateActions(window365dStart.atStartOfDay(), cutoffDateTime)
                    ).flatMap(tuple -> {
                        Map<String, Map<LocalDate, Double>> benchmarkReturnsMap = tuple.getT1();
                        Map<String, Integer> dcaRankMap = tuple.getT2();
                        Map<String, List<DividendAnnouncement>> dividendMap = tuple.getT3();
                        Map<String, BigDecimal> aumMap = tuple.getT4();
                        Map<String, List<CorporateAction>> corporateActionMap = tuple.getT5();

                        return runScoringAndOrthogonalization(
                                assets, benchmarkReturnsMap, dcaRankMap, dividendMap, corporateActionMap, aumMap,
                                evaluationDateTime, cutoffDateTime, queryStartDateTime,
                                window365dStart, window90dStart, window30dStart
                        );
                    });
                });
    }

    private Mono<Map<String, BigDecimal>> prefetchAumMap() {
        if (externalMarketDataPort != null) {
            return externalMarketDataPort.fetchCurrentAumMap().defaultIfEmpty(Collections.emptyMap());
        }
        return Mono.just(Collections.emptyMap());
    }

    private Mono<Map<String, Map<LocalDate, Double>>> prefetchBenchmarks(LocalDateTime from, LocalDateTime to) {
        return quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^TWII", from, to)
                .collectList()
                .map(quotes -> {
                    Map<LocalDate, Double> returns = FinancialMetricsCalculator.calculateDailyReturns(quotes);
                    return Map.of("^TWII", returns);
                });
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

    private Mono<Map<String, List<DividendAnnouncement>>> prefetchDividends(LocalDateTime from, LocalDateTime to) {
        if (dividendRepository == null) {
            return Mono.just(Collections.emptyMap());
        }
        return dividendRepository.findByExDateBetweenOrderByExDateAsc(from, to)
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

    private Mono<Map<String, List<CorporateAction>>> prefetchCorporateActions(LocalDateTime from, LocalDateTime to) {
        if (corporateActionRepository == null) {
            return Mono.just(Collections.emptyMap());
        }
        return corporateActionRepository.findByEffectiveDateBetweenOrderByEffectiveDateAsc(from, to)
                .collectList()
                .map(list -> {
                    Map<String, List<CorporateAction>> map = new HashMap<>();
                    for (CorporateAction ca : list) {
                        if (ca.getTicker() != null) {
                            map.computeIfAbsent(ca.getTicker(), k -> new ArrayList<>()).add(ca);
                        }
                    }
                    return map;
                })
                .defaultIfEmpty(Collections.emptyMap());
    }

    private record EvaluatedCandidate(
            GlobalAssetMetadata metadata,
            CandidateAssetClass targetClass,
            List<MarketDailyQuote> quotes365d,
            Map<LocalDate, Double> dailyReturns365d,
            double maxR2,
            double r2Taiex,
            double mom121,
            double ker,
            double sharpe,
            double vol90d,
            double ytm,
            Integer dcaRank,
            BigDecimal currentAum
    ) {}

    private Mono<GlobalAssetScoreEvaluationResponse> runScoringAndOrthogonalization(
            List<GlobalAssetMetadata> assets,
            Map<String, Map<LocalDate, Double>> benchmarkReturnsMap,
            Map<String, Integer> dcaRankMap,
            Map<String, List<DividendAnnouncement>> dividendMap,
            Map<String, List<CorporateAction>> corporateActionMap,
            Map<String, BigDecimal> aumMap,
            LocalDateTime evaluationDateTime, LocalDateTime cutoffDateTime, LocalDateTime queryStartDateTime,
            LocalDate window365dStart, LocalDate window90dStart, LocalDate window30dStart) {

        List<EvaluatedCandidate> coreCandidates = new ArrayList<>();
        List<EvaluatedCandidate> satelliteCandidates = new ArrayList<>();
        List<EvaluatedCandidate> bondCandidates = new ArrayList<>();

        return Flux.fromIterable(assets)
                .flatMap(asset -> {
                    return quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(asset.getTicker(), queryStartDateTime, cutoffDateTime)
                            .collectList()
                            .map(rawQuotes -> {
                                // Universal Gatekeeper 1: Listing age >= 1 natural calendar year (accounting for leap year)
                                if (asset.getListingDate() == null || asset.getListingDate().isAfter(cutoffDateTime.minusYears(1))) {
                                    return Optional.<EvaluatedCandidate>empty();
                                }

                                // In-memory split adjustment to eliminate false price cliff drops
                                List<CorporateAction> splits = corporateActionMap.getOrDefault(asset.getTicker(), Collections.emptyList());
                                List<MarketDailyQuote> adjustedQuotes = FinancialMetricsCalculator.adjustQuotesForSplits(rawQuotes, splits);

                                // Quotes in 365d window (already scoped by DB query)
                                List<MarketDailyQuote> quotes365d = adjustedQuotes.stream()
                                        .sorted(Comparator.comparing(MarketDailyQuote::getTradeDate))
                                        .toList();

                                // Universal Gatekeeper 1.2: Trading days N >= 220
                                if (quotes365d.size() < MIN_TRADING_DAYS_365D) {
                                    return Optional.<EvaluatedCandidate>empty();
                                }

                                // Universal Gatekeeper 2: Latest AUM >= 20 億 TWD
                                BigDecimal currentAum = aumMap.get(asset.getTicker());
                                if (currentAum == null || currentAum.compareTo(UNIVERSAL_MIN_AUM) < 0) {
                                    return Optional.<EvaluatedCandidate>empty();
                                }

                                // Universal Gatekeeper 3: 30d Avg Daily Turnover >= 2,000 萬 TWD
                                List<MarketDailyQuote> quotes30d = quotes365d.stream()
                                        .filter(q -> !q.getTradeDate().toLocalDate().isBefore(window30dStart))
                                        .toList();
                                double totalTurnover30d = 0.0;
                                for (MarketDailyQuote q : quotes30d) {
                                    if (q.getTradeValueTwd() != null) {
                                        totalTurnover30d += q.getTradeValueTwd().doubleValue();
                                    }
                                }
                                double avgTurnover30d = quotes30d.isEmpty() ? 0.0 : totalTurnover30d / quotes30d.size();
                                if (avgTurnover30d < UNIVERSAL_MIN_30D_TURNOVER.doubleValue()) {
                                    return Optional.<EvaluatedCandidate>empty();
                                }

                                // Daily returns in 365d
                                Map<LocalDate, Double> dailyReturns365d = FinancialMetricsCalculator.calculateDailyReturns(adjustedQuotes);

                                Integer dcaRank = dcaRankMap.get(asset.getTicker());
                                boolean isBond = asset.getTicker().endsWith("B");

                                if (isBond) {
                                    // 債券專用一票否決：排除非投資等級債 (High Yield)
                                    if (isNonInvestmentGradeBond(asset)) {
                                        return Optional.<EvaluatedCandidate>empty();
                                    }

                                    // Qualified for Defensive Bond Pool: bypass equity benchmark regressions
                                    double ytm = calculateDividendYield(asset, quotes365d, dividendMap.get(asset.getTicker()), cutoffDateTime);
                                    return Optional.of(new EvaluatedCandidate(
                                            asset, CandidateAssetClass.DEFENSIVE, quotes365d, dailyReturns365d,
                                            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, ytm, dcaRank, currentAum
                                    ));
                                }

                                // Benchmark regression for equity ETFs: only compare against Taiwan Weighted Index (^TWII)
                                double r2Twii = calcR2WithBm(dailyReturns365d, benchmarkReturnsMap.get("^TWII"), 0);
                                double maxR2 = r2Twii;

                                // Routing logic
                                if (maxR2 >= CORE_R2_THRESHOLD) {
                                    // Qualified for Core Pool
                                    return Optional.of(new EvaluatedCandidate(
                                            asset, CandidateAssetClass.CORE, quotes365d, dailyReturns365d,
                                            maxR2, r2Twii, 0.0, 0.0, 0.0, 0.0, 0.0, dcaRank, currentAum
                                    ));
                                } else {
                                    // Check Satellite Gates
                                    // 1. Volatility sigma_90d >= 18%
                                    List<Double> returns90d = dailyReturns365d.entrySet().stream()
                                            .filter(e -> !e.getKey().isBefore(window90dStart))
                                            .map(Map.Entry::getValue)
                                            .toList();
                                    double vol90d = FinancialMetricsCalculator.calculateAnnualizedVolatility(returns90d);
                                    if (vol90d < SATELLITE_VOLATILITY_THRESHOLD) {
                                        return Optional.<EvaluatedCandidate>empty();
                                    }

                                    // 2. MOM(12-1) > 0
                                    BigDecimal p30d = findPriceNearDate(quotes365d, window30dStart);
                                    BigDecimal p365d = findPriceNearDate(quotes365d, window365dStart);
                                    double mom121 = FinancialMetricsCalculator.calculateMomentum12_1(p30d, p365d);
                                    if (mom121 <= 0.0) {
                                        return Optional.<EvaluatedCandidate>empty();
                                    }

                                    // KER & Sharpe
                                    List<BigDecimal> prices365d = quotes365d.stream().map(MarketDailyQuote::getClosePrice).toList();
                                    double ker = FinancialMetricsCalculator.calculateKaufmanEfficiencyRatio(prices365d);
                                    double sharpe = FinancialMetricsCalculator.calculateSharpeRatio(new ArrayList<>(dailyReturns365d.values()));

                                    return Optional.of(new EvaluatedCandidate(
                                            asset, CandidateAssetClass.SATELLITE, quotes365d, dailyReturns365d,
                                            maxR2, r2Twii, mom121, ker, sharpe, vol90d, 0.0, dcaRank, currentAum
                                    ));
                                }
                            });
                })
                .collectList()
                .flatMap(candidates -> {
                    for (Optional<EvaluatedCandidate> opt : candidates) {
                        if (opt.isPresent()) {
                            EvaluatedCandidate c = opt.get();
                            if (c.targetClass() == CandidateAssetClass.CORE) coreCandidates.add(c);
                            else if (c.targetClass() == CandidateAssetClass.SATELLITE) satelliteCandidates.add(c);
                            else if (c.targetClass() == CandidateAssetClass.DEFENSIVE) bondCandidates.add(c);
                        }
                    }

                    // Stage 2 Percentile Ranking
                    List<GlobalAssetScore> coreScores = scoreCoreCandidates(coreCandidates, evaluationDateTime);
                    List<GlobalAssetScore> satScores = scoreSatelliteCandidates(satelliteCandidates, evaluationDateTime);
                    List<GlobalAssetScore> bondScores = scoreBondCandidates(bondCandidates, evaluationDateTime);

                    // Stage 3 Pairwise Matrix & Greedy Orthogonal Engine
                    // Limit: Core Top 10, Sat Top 20, Bond Top 5
                    List<GlobalAssetScore> topCore = coreScores.stream().limit(10).toList();
                    List<GlobalAssetScore> topSat = satScores.stream().limit(20).toList();
                    List<GlobalAssetScore> topBond = bondScores.stream().limit(5).toList();

                    // Generate pairwise matrices
                    Map<String, Map<LocalDate, Double>> coreReturns = new HashMap<>();
                    for (EvaluatedCandidate c : coreCandidates) {
                        coreReturns.put(c.metadata().getTicker(), c.dailyReturns365d());
                    }
                    Map<String, Map<LocalDate, Double>> satReturns = new HashMap<>();
                    for (EvaluatedCandidate c : satelliteCandidates) {
                        satReturns.put(c.metadata().getTicker(), c.dailyReturns365d());
                    }

                    List<GlobalAssetPairwiseMatrix> pairwiseMatrices = new ArrayList<>();
                    pairwiseMatrices.addAll(generatePairwiseMatrix(topCore, coreReturns, CandidateAssetClass.CORE, evaluationDateTime));
                    pairwiseMatrices.addAll(generatePairwiseMatrix(topSat, satReturns, CandidateAssetClass.SATELLITE, evaluationDateTime));

                    List<GlobalAssetScore> finalScoresToSave = new ArrayList<>();
                    finalScoresToSave.addAll(topCore);
                    finalScoresToSave.addAll(topSat);
                    finalScoresToSave.addAll(topBond);

                    Mono<Void> cleanOld = Mono.empty();
                    if (pairwiseMatrixRepository != null) {
                        cleanOld = cleanOld.then(pairwiseMatrixRepository.deleteByEvaluationDate(evaluationDateTime));
                    }
                    cleanOld = cleanOld.then(scoreRepository.deleteByEvaluationDate(evaluationDateTime));

                    Mono<Void> savePairwise = (pairwiseMatrixRepository != null && !pairwiseMatrices.isEmpty())
                            ? pairwiseMatrixRepository.saveAll(pairwiseMatrices).then()
                            : Mono.empty();

                    return cleanOld
                            .then(scoreRepository.saveAll(finalScoresToSave).collectList())
                            .then(savePairwise)
                            .then(updateMonthlyWatermark(evaluationDateTime, finalScoresToSave.size()))
                            .thenReturn(new GlobalAssetScoreEvaluationResponse(
                                    "SUCCESS",
                                    "Monthly Candidate Screening, Multi-Factor Ranking & Orthogonal Engine completed successfully.",
                                    evaluationDateTime.toString(),
                                    finalScoresToSave.size(),
                                    topCore.size(),
                                    topSat.size(),
                                    topBond.size()
                            ));
                });
    }

    private double calcR2WithBm(Map<LocalDate, Double> etfReturns, Map<LocalDate, Double> bmReturns, int shift) {
        if (etfReturns == null || bmReturns == null) return 0.0;
        FinancialMetricsCalculator.AlignedReturns aligned = FinancialMetricsCalculator.alignReturnSeries(etfReturns, bmReturns, shift);
        FinancialMetricsCalculator.CorrelationResult res = FinancialMetricsCalculator.calculateCorrelationAndRSquared(aligned.returnsA(), aligned.returnsB());
        return res.rSquared();
    }

    private BigDecimal findPriceNearDate(List<MarketDailyQuote> quotes, LocalDate targetDate) {
        if (quotes.isEmpty()) return BigDecimal.ONE;
        MarketDailyQuote closest = null;
        long minDiff = Long.MAX_VALUE;
        for (MarketDailyQuote q : quotes) {
            long diff = Math.abs(ChronoUnit.DAYS.between(q.getTradeDate().toLocalDate(), targetDate));
            if (diff < minDiff) {
                minDiff = diff;
                closest = q;
            }
        }
        return (closest != null && closest.getClosePrice() != null) ? closest.getClosePrice() : BigDecimal.ONE;
    }

    private double calculateDividendYield(GlobalAssetMetadata asset, List<MarketDailyQuote> quotes,
                                         List<DividendAnnouncement> divs, LocalDateTime cutoff) {
        if (quotes.isEmpty() || divs == null || divs.isEmpty()) return 0.0;
        BigDecimal lastPrice = quotes.get(quotes.size() - 1).getClosePrice();
        if (lastPrice == null || lastPrice.compareTo(BigDecimal.ZERO) <= 0) return 0.0;

        LocalDateTime oneYearAgo = cutoff.minusYears(1);
        double totalDiv = divs.stream()
                .filter(d -> d.getExDate() != null && !d.getExDate().isBefore(oneYearAgo) && !d.getExDate().isAfter(cutoff))
                .filter(d -> d.getDividendPerShare() != null)
                .mapToDouble(d -> d.getDividendPerShare().doubleValue())
                .sum();

        return totalDiv / lastPrice.doubleValue();
    }

    private List<GlobalAssetScore> scoreCoreCandidates(List<EvaluatedCandidate> candidates, LocalDateTime evaluationDateTime) {
        if (candidates.isEmpty()) return Collections.emptyList();

        Map<EvaluatedCandidate, Double> r2Ranks = FinancialMetricsCalculator.calculatePercentileRanks(candidates, EvaluatedCandidate::maxR2, true);
        Map<EvaluatedCandidate, Double> dcaRanks = FinancialMetricsCalculator.calculatePercentileRanks(candidates, c -> (c.dcaRank() != null && c.dcaRank() <= 20) ? (21 - c.dcaRank()) : 0.0, true);
        Map<EvaluatedCandidate, Double> aumRanks = FinancialMetricsCalculator.calculatePercentileRanks(
                candidates,
                c -> (c.currentAum() != null ? c.currentAum().doubleValue() : 0.0),
                true
        );

        List<GlobalAssetScore> scores = new ArrayList<>();
        for (EvaluatedCandidate c : candidates) {
            double r2Pct = r2Ranks.getOrDefault(c, 0.0);
            double dcaPct = dcaRanks.getOrDefault(c, 0.0);
            double aumPct = aumRanks.getOrDefault(c, 0.0);

            double composite = ( (1.0 / 3.0) * r2Pct + (1.0 / 3.0) * dcaPct + (1.0 / 3.0) * aumPct ) * 100.0;

            GlobalAssetScore score = new GlobalAssetScore();
            score.setAssetId(c.metadata().getId());
            score.setTicker(c.metadata().getTicker());
            score.setEvaluationDate(evaluationDateTime);
            score.setAssetClass(CandidateAssetClass.CORE);
            score.setCompositeScore(BigDecimal.valueOf(composite).setScale(2, RoundingMode.HALF_UP));
            score.setFundSizeTwd(c.currentAum());
            score.setRSquared(BigDecimal.valueOf(c.maxR2()).setScale(4, RoundingMode.HALF_UP));
            score.setDcaRank(c.dcaRank());
            scores.add(score);
        }

        scores.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));
        int rank = 1;
        for (GlobalAssetScore s : scores) {
            s.setClassRank(rank++);
        }
        return scores;
    }

    private List<GlobalAssetScore> scoreSatelliteCandidates(List<EvaluatedCandidate> candidates, LocalDateTime evaluationDateTime) {
        if (candidates.isEmpty()) return Collections.emptyList();

        Map<EvaluatedCandidate, Double> momRanks = FinancialMetricsCalculator.calculatePercentileRanks(candidates, EvaluatedCandidate::mom121, true);
        Map<EvaluatedCandidate, Double> kerRanks = FinancialMetricsCalculator.calculatePercentileRanks(candidates, EvaluatedCandidate::ker, true);
        Map<EvaluatedCandidate, Double> sharpeRanks = FinancialMetricsCalculator.calculatePercentileRanks(candidates, EvaluatedCandidate::sharpe, true);

        List<GlobalAssetScore> scores = new ArrayList<>();
        for (EvaluatedCandidate c : candidates) {
            double momPct = momRanks.getOrDefault(c, 0.0);
            double kerPct = kerRanks.getOrDefault(c, 0.0);
            double sharpePct = sharpeRanks.getOrDefault(c, 0.0);
            double shadowDiscount = Math.max(0.0, 1.0 - c.r2Taiex());

            double composite = ( (1.0 / 3.0) * momPct + (1.0 / 3.0) * kerPct + (1.0 / 3.0) * sharpePct ) * shadowDiscount * 100.0;

            GlobalAssetScore score = new GlobalAssetScore();
            score.setAssetId(c.metadata().getId());
            score.setTicker(c.metadata().getTicker());
            score.setEvaluationDate(evaluationDateTime);
            score.setAssetClass(CandidateAssetClass.SATELLITE);
            score.setCompositeScore(BigDecimal.valueOf(composite).setScale(2, RoundingMode.HALF_UP));
            score.setFundSizeTwd(c.currentAum());
            score.setRSquared(BigDecimal.valueOf(c.r2Taiex()).setScale(4, RoundingMode.HALF_UP));
            score.setMomentum121(BigDecimal.valueOf(c.mom121()).setScale(4, RoundingMode.HALF_UP));
            score.setKaufmanEr(BigDecimal.valueOf(c.ker()).setScale(4, RoundingMode.HALF_UP));
            score.setSharpeRatio(BigDecimal.valueOf(c.sharpe()).setScale(4, RoundingMode.HALF_UP));
            score.setVolatility90d(BigDecimal.valueOf(c.vol90d()).setScale(4, RoundingMode.HALF_UP));
            scores.add(score);
        }

        scores.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));
        int rank = 1;
        for (GlobalAssetScore s : scores) {
            s.setClassRank(rank++);
        }
        return scores;
    }

    private List<GlobalAssetScore> scoreBondCandidates(List<EvaluatedCandidate> candidates, LocalDateTime evaluationDateTime) {
        if (candidates.isEmpty()) return Collections.emptyList();

        Map<EvaluatedCandidate, Double> ytmRanks = FinancialMetricsCalculator.calculatePercentileRanks(candidates, EvaluatedCandidate::ytm, true);
        Map<EvaluatedCandidate, Double> aumRanks = FinancialMetricsCalculator.calculatePercentileRanks(
                candidates,
                c -> (c.currentAum() != null ? c.currentAum().doubleValue() : 0.0),
                true
        );

        List<GlobalAssetScore> scores = new ArrayList<>();
        for (EvaluatedCandidate c : candidates) {
            double ytmPct = ytmRanks.getOrDefault(c, 0.0);
            double aumPct = aumRanks.getOrDefault(c, 0.0);

            double composite = (0.70 * ytmPct + 0.30 * aumPct) * 100.0;

            GlobalAssetScore score = new GlobalAssetScore();
            score.setAssetId(c.metadata().getId());
            score.setTicker(c.metadata().getTicker());
            score.setEvaluationDate(evaluationDateTime);
            score.setAssetClass(CandidateAssetClass.DEFENSIVE);
            score.setCompositeScore(BigDecimal.valueOf(composite).setScale(2, RoundingMode.HALF_UP));
            score.setFundSizeTwd(c.currentAum());
            score.setYtm(BigDecimal.valueOf(c.ytm()).setScale(4, RoundingMode.HALF_UP));
            scores.add(score);
        }

        scores.sort((a, b) -> b.getCompositeScore().compareTo(a.getCompositeScore()));
        int rank = 1;
        for (GlobalAssetScore s : scores) {
            s.setClassRank(rank++);
        }
        return scores;
    }

    private List<GlobalAssetPairwiseMatrix> generatePairwiseMatrix(
            List<GlobalAssetScore> topList,
            Map<String, Map<LocalDate, Double>> returnMap,
            CandidateAssetClass assetClass,
            LocalDateTime evaluationDateTime) {

        List<GlobalAssetPairwiseMatrix> matrix = new ArrayList<>();
        for (int i = 0; i < topList.size(); i++) {
            String tickerA = topList.get(i).getTicker();
            Map<LocalDate, Double> retA = returnMap.get(tickerA);
            for (int j = i + 1; j < topList.size(); j++) {
                String tickerB = topList.get(j).getTicker();
                Map<LocalDate, Double> retB = returnMap.get(tickerB);

                // Enforce base_ticker < target_ticker
                String baseTicker = (tickerA.compareTo(tickerB) < 0) ? tickerA : tickerB;
                String targetTicker = (tickerA.compareTo(tickerB) < 0) ? tickerB : tickerA;
                Map<LocalDate, Double> baseRet = (tickerA.compareTo(tickerB) < 0) ? retA : retB;
                Map<LocalDate, Double> targetRet = (tickerA.compareTo(tickerB) < 0) ? retB : retA;

                FinancialMetricsCalculator.AlignedReturns aligned = FinancialMetricsCalculator.alignReturnSeries(baseRet, targetRet, 0);
                FinancialMetricsCalculator.CorrelationResult res = FinancialMetricsCalculator.calculateCorrelationAndRSquared(aligned.returnsA(), aligned.returnsB());

                GlobalAssetPairwiseMatrix entry = new GlobalAssetPairwiseMatrix(
                        null,
                        evaluationDateTime,
                        assetClass,
                        baseTicker,
                        targetTicker,
                        BigDecimal.valueOf(res.rSquared()).setScale(4, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(res.correlation()).setScale(4, RoundingMode.HALF_UP)
                );
                matrix.add(entry);
            }
        }
        return matrix;
    }

    private Mono<Void> updateMonthlyWatermark(LocalDateTime evaluationDateTime, int recordsCount) {
        if (watermarkRepository == null) return Mono.empty();
        return watermarkRepository.findByFeedName(WATERMARK_MONTHLY_TOP_LIST)
                .defaultIfEmpty(new DataFeedSyncWatermark(
                        UUID.randomUUID(), WATERMARK_MONTHLY_TOP_LIST, evaluationDateTime, evaluationDateTime,
                        recordsCount, "PASS", null, LocalDateTime.now()
                ))
                .flatMap(wm -> {
                    wm.setLastSuccessfulSyncAt(LocalDateTime.now());
                    wm.setLatestRecordDate(evaluationDateTime);
                    wm.setRecordsSyncedCount(recordsCount);
                    wm.setStatus("PASS");
                    wm.setUpdatedAt(LocalDateTime.now());
                    return watermarkRepository.save(wm);
                })
                .then();
    }

    public double calculateR2(List<MarketDailyQuote> etfQuotes, List<MarketDailyQuote> bmQuotes) {
        Map<LocalDate, Double> etfReturns = FinancialMetricsCalculator.calculateDailyReturns(etfQuotes);
        Map<LocalDate, Double> bmReturns = FinancialMetricsCalculator.calculateDailyReturns(bmQuotes);
        FinancialMetricsCalculator.AlignedReturns aligned = FinancialMetricsCalculator.alignReturnSeries(etfReturns, bmReturns, 0);
        return FinancialMetricsCalculator.calculateCorrelationAndRSquared(aligned.returnsA(), aligned.returnsB()).rSquared();
    }

    public String resolveBenchmarkTicker(GlobalAssetMetadata asset) {
        if (asset == null) return "^TWII";
        String name = asset.getName() != null ? asset.getName().toUpperCase() : "";
        String idx = asset.getUnderlyingIndex() != null ? asset.getUnderlyingIndex().toUpperCase() : "";
        if (name.contains("S&P") || name.contains("標普") || name.contains("500") || idx.contains("S&P") || idx.contains("500")) {
            return "^GSPC";
        }
        if (name.contains("NASDAQ") || name.contains("那斯達克") || idx.contains("NASDAQ")) {
            return "^NDX";
        }
        if (name.contains("日經") || name.contains("東證") || idx.contains("N225") || idx.contains("TOPIX")) {
            return "^N225";
        }
        return "^TWII";
    }

    public static DistributionFrequency deriveDistributionFrequency(List<DividendAnnouncement> dividendsPastYear) {
        return deriveDistributionFrequency(dividendsPastYear, null, null);
    }

    public static DistributionFrequency deriveDistributionFrequency(List<DividendAnnouncement> dividends,
                                                                    LocalDateTime listingDate,
                                                                    LocalDateTime evaluationDate) {
        if (dividends == null || dividends.isEmpty()) {
            return DistributionFrequency.NONE;
        }

        List<DividendAnnouncement> validDivs = dividends.stream()
                .filter(d -> d != null && d.getExDate() != null && d.getDividendPerShare() != null && d.getDividendPerShare().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(DividendAnnouncement::getExDate))
                .toList();

        if (validDivs.isEmpty()) {
            return DistributionFrequency.NONE;
        }

        if (validDivs.size() >= 2) {
            List<Long> intervals = new ArrayList<>();
            for (int i = 0; i < validDivs.size() - 1; i++) {
                long days = Math.abs(ChronoUnit.DAYS.between(validDivs.get(i).getExDate(), validDivs.get(i + 1).getExDate()));
                if (days > 0) {
                    intervals.add(days);
                }
            }
            if (!intervals.isEmpty()) {
                Collections.sort(intervals);
                long medianInterval = intervals.get(intervals.size() / 2);
                if (medianInterval <= 45) {
                    return DistributionFrequency.MONTHLY;
                } else if (medianInterval <= 135) {
                    return DistributionFrequency.QUARTERLY;
                } else if (medianInterval <= 250) {
                    return DistributionFrequency.SEMI_ANNUAL;
                } else {
                    return DistributionFrequency.ANNUAL;
                }
            }
        }

        if (listingDate != null && evaluationDate != null && !listingDate.isAfter(evaluationDate.minusYears(1))) {
            return DistributionFrequency.ANNUAL;
        }

        long listingDays = (listingDate != null && evaluationDate != null)
                ? Math.max(1, ChronoUnit.DAYS.between(listingDate, evaluationDate))
                : 365;

        double annualizedCount = 1.0 * (365.25 / Math.max(listingDays, 30));
        if (annualizedCount >= 8.0) {
            return DistributionFrequency.MONTHLY;
        } else if (annualizedCount >= 2.5) {
            return DistributionFrequency.QUARTERLY;
        } else if (annualizedCount >= 1.5) {
            return DistributionFrequency.SEMI_ANNUAL;
        } else {
            return DistributionFrequency.ANNUAL;
        }
    }
}

