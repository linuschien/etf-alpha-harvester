package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.*;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.OrthogonalStatus;
import com.alphaharvester.domain.math.FinancialMetricsCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GlobalAssetQueryService {

    private static final Logger log = LoggerFactory.getLogger(GlobalAssetQueryService.class);

    private final GlobalAssetMetadataRepository metadataRepository;
    private final BenchmarkIndexRepository benchmarkRepository;
    private final MarketDailyQuoteRepository quoteRepository;
    private final MacroYieldSnapshotRepository macroYieldRepository;
    private final GlobalAssetScoreRepository scoreRepository;
    private final DcaPopularityRankRepository dcaRankRepository;
    private final DividendAnnouncementRepository dividendRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository;
    private final DataFeedSyncWatermarkRepository watermarkRepository;

    @Autowired
    public GlobalAssetQueryService(GlobalAssetMetadataRepository metadataRepository,
                                   BenchmarkIndexRepository benchmarkRepository,
                                   MarketDailyQuoteRepository quoteRepository,
                                   MacroYieldSnapshotRepository macroYieldRepository,
                                   GlobalAssetScoreRepository scoreRepository,
                                   DcaPopularityRankRepository dcaRankRepository,
                                   DividendAnnouncementRepository dividendRepository,
                                   CorporateActionRepository corporateActionRepository,
                                   @Autowired(required = false) GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository,
                                   @Autowired(required = false) DataFeedSyncWatermarkRepository watermarkRepository) {
        this.metadataRepository = metadataRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.quoteRepository = quoteRepository;
        this.macroYieldRepository = macroYieldRepository;
        this.scoreRepository = scoreRepository;
        this.dcaRankRepository = dcaRankRepository;
        this.dividendRepository = dividendRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.pairwiseMatrixRepository = pairwiseMatrixRepository;
        this.watermarkRepository = watermarkRepository;
    }

    public Flux<GlobalAssetMetadata> listGlobalAssets(GlobalAssetFilterInput filter) {
        if (filter == null || filter.ticker() == null || filter.ticker().isBlank()) {
            return metadataRepository.findAll();
        }
        GlobalAssetMetadata probe = new GlobalAssetMetadata();
        probe.setTicker(filter.ticker());

        ExampleMatcher matcher = ExampleMatcher.matchingAll()
                .withIgnoreNullValues()
                .withIgnorePaths("version");

        return metadataRepository.findAll(Example.of(probe, matcher));
    }

    public Mono<GlobalAssetMetadata> getGlobalAssetById(UUID id) {
        return metadataRepository.findById(id);
    }

    public Mono<GlobalAssetMetadata> getGlobalAssetByTicker(String ticker) {
        return metadataRepository.findByTicker(ticker);
    }

    public Flux<BenchmarkIndex> listBenchmarkIndices() {
        return benchmarkRepository.findAll();
    }

    public Mono<BenchmarkIndex> getBenchmarkIndexById(UUID id) {
        return benchmarkRepository.findById(id);
    }

    public Mono<BenchmarkIndex> getBenchmarkIndexByTicker(String ticker) {
        return benchmarkRepository.findByTicker(ticker);
    }

    public Flux<MarketDailyQuote> listMarketDailyQuotes(MarketDailyQuoteFilterInput filter) {
        if (filter == null) {
            return quoteRepository.findAll();
        }
        if (filter.ticker() != null && filter.startDate() != null && filter.endDate() != null) {
            LocalDateTime start = parseDate(filter.startDate(), false);
            LocalDateTime end = parseDate(filter.endDate(), true);
            return quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(filter.ticker(), start, end);
        }
        if (filter.ticker() != null) {
            return quoteRepository.findByTickerOrderByTradeDateDesc(filter.ticker());
        }
        return quoteRepository.findAll();
    }

    public Mono<MarketDailyQuote> getMarketDailyQuoteById(UUID id) {
        return quoteRepository.findById(id);
    }

    public Flux<MarketDailyQuote> getQuoteTimeSeries(String ticker, String startDate, String endDate) {
        LocalDateTime start = parseDate(startDate, false);
        LocalDateTime end = parseDate(endDate, true);
        return quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, start, end);
    }

    public Flux<MarketDailyQuote> listQuotesByAssetId(UUID assetId) {
        return quoteRepository.findByAssetIdOrderByTradeDateDesc(assetId);
    }

    public Flux<MarketDailyQuote> listQuotesByBenchmarkId(UUID benchmarkId) {
        return quoteRepository.findByBenchmarkIdOrderByTradeDateDesc(benchmarkId);
    }

    public Flux<MacroYieldSnapshot> listMacroYieldSnapshots(MacroYieldFilterInput filter) {
        if (filter != null && filter.startDate() != null && filter.endDate() != null) {
            LocalDateTime start = parseDate(filter.startDate(), false);
            LocalDateTime end = parseDate(filter.endDate(), true);
            return macroYieldRepository.findByRecordDateBetweenOrderByRecordDateAsc(start, end);
        }
        return macroYieldRepository.findAll();
    }

    public Mono<MacroYieldSnapshot> getMacroYieldSnapshotById(UUID id) {
        return macroYieldRepository.findById(id);
    }

    public Mono<MacroYieldSnapshot> getLatestMacroYieldSnapshot() {
        return macroYieldRepository.findTopByOrderByRecordDateDesc();
    }

    public Flux<GlobalAssetScore> listGlobalAssetScores(GlobalAssetScoreFilterInput filter) {
        if (filter == null) {
            return scoreRepository.findAll();
        }
        GlobalAssetScore probe = new GlobalAssetScore();
        probe.setAssetClass(filter.assetClass());
        if (filter.evaluationDate() != null) {
            probe.setEvaluationDate(parseDate(filter.evaluationDate(), false));
        }

        ExampleMatcher matcher = ExampleMatcher.matchingAll()
                .withIgnoreNullValues()
                .withIgnorePaths("compositeScore", "fundSizeTwd", "classRank", "orthogonalStatus", "rSquared", "momentum12m", "kaufmanEr", "sharpeRatio", "volatility90d", "ytm", "dcaRank");

        return scoreRepository.findAll(Example.of(probe, matcher));
    }

    public Mono<GlobalAssetScore> getGlobalAssetScoreById(UUID id) {
        return scoreRepository.findById(id);
    }

    public Flux<GlobalAssetScore> getScoresByAssetClass(CandidateAssetClass assetClass, String evaluationDate) {
        LocalDateTime evalDate = parseDate(evaluationDate, false);
        return scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(assetClass, evalDate)
                .flatMapSequential(this::enrichScore);
    }

    public Mono<GlobalAssetScore> getScoreByTicker(String ticker, String evaluationDate) {
        LocalDateTime evalDate = parseDate(evaluationDate, false);
        return scoreRepository.findByTickerAndEvaluationDate(ticker, evalDate)
                .flatMap(this::enrichScore);
    }

    public Flux<GlobalAssetPairwiseMatrix> listPairwiseMatrix(CandidateAssetClass assetClass, String evaluationDateStr) {
        if (pairwiseMatrixRepository == null) return Flux.empty();
        LocalDateTime evalDate = (evaluationDateStr != null && !evaluationDateStr.isBlank())
                ? parseDate(evaluationDateStr, false)
                : LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return pairwiseMatrixRepository.findByEvaluationDateAndAssetClass(evalDate, assetClass);
    }

    public Flux<GlobalAssetScore> getOrthogonalCandidates(CandidateAssetClass assetClass, String seedTicker, String evaluationDateStr) {
        LocalDateTime evalDate = (evaluationDateStr != null && !evaluationDateStr.isBlank())
                ? parseDate(evaluationDateStr, false)
                : LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(assetClass, evalDate)
                .collectList()
                .flatMapMany(scores -> {
                    if (scores.isEmpty()) return Flux.empty();
                    if (pairwiseMatrixRepository == null) return Flux.fromIterable(scores).flatMapSequential(this::enrichScore);

                    return pairwiseMatrixRepository.findByEvaluationDateAndAssetClass(evalDate, assetClass)
                            .collectList()
                            .flatMapMany(matrix -> {
                                Map<String, Double> r2Map = new HashMap<>();
                                for (GlobalAssetPairwiseMatrix m : matrix) {
                                    r2Map.put(m.getBaseTicker() + ":" + m.getTargetTicker(), m.getRSquared().doubleValue());
                                }

                                List<GlobalAssetScore> candidates = new ArrayList<>();
                                boolean isModeB = false;

                                if (seedTicker != null && !seedTicker.isBlank()) {
                                    Optional<GlobalAssetScore> seedOpt = scores.stream()
                                            .filter(s -> s.getTicker().equalsIgnoreCase(seedTicker.trim()))
                                            .findFirst();
                                    if (seedOpt.isPresent()) {
                                        GlobalAssetScore seed = seedOpt.get();
                                        candidates.add(seed);
                                        for (GlobalAssetScore s : scores) {
                                            if (!s.getTicker().equalsIgnoreCase(seed.getTicker())) {
                                                candidates.add(s);
                                            }
                                        }
                                        isModeB = true;
                                    }
                                }

                                if (!isModeB) {
                                    candidates.addAll(scores);
                                }

                                applyGreedyOrthogonal(candidates, r2Map, isModeB);
                                return Flux.fromIterable(candidates).flatMapSequential(this::enrichScore);
                            });
                });
    }

    private void applyGreedyOrthogonal(List<GlobalAssetScore> candidates, Map<String, Double> r2Map, boolean isModeB) {
        if (candidates.isEmpty()) return;

        GlobalAssetScore seed = candidates.get(0);
        seed.setOrthogonalStatus(OrthogonalStatus.ACCEPTED);
        seed.setCollisionDetail(isModeB ? "Anchor Seed (Mode B)" : "Seed (Rank 1)");

        List<GlobalAssetScore> selected = new ArrayList<>();
        selected.add(seed);

        for (int i = 1; i < candidates.size(); i++) {
            GlobalAssetScore c = candidates.get(i);
            boolean isCollinear = false;
            String conflictTicker = null;
            double conflictR2 = 0.0;

            for (GlobalAssetScore s : selected) {
                double r2 = FinancialMetricsCalculator.getPairwiseRSquared(r2Map, c.getTicker(), s.getTicker());
                if (r2 >= 0.50) {
                    isCollinear = true;
                    conflictTicker = s.getTicker();
                    conflictR2 = r2;
                    break;
                }
            }

            if (isCollinear) {
                c.setOrthogonalStatus(OrthogonalStatus.REJECTED_COLLINEAR);
                c.setCollisionDetail(String.format("Collinear with %s (R^2 = %.2f)", conflictTicker, conflictR2));
            } else {
                c.setOrthogonalStatus(OrthogonalStatus.ACCEPTED);
                c.setCollisionDetail("Natural Orthogonal");
                selected.add(c);
            }
        }
    }

    public Flux<DcaPopularityRank> listDcaPopularityRanks(DcaPopularityFilterInput filter) {
        if (filter != null && filter.rankingYear() != null && filter.rankingMonth() != null) {
            return dcaRankRepository.findByRankingYearAndRankingMonthOrderByRankPositionAsc(filter.rankingYear(), filter.rankingMonth());
        }
        return dcaRankRepository.findAll();
    }

    public Mono<DcaPopularityRank> getDcaPopularityRankById(UUID id) {
        return dcaRankRepository.findById(id);
    }

    public Flux<DcaPopularityRank> getTop20DcaRanks(Integer year, Integer month) {
        Flux<DcaPopularityRank> rankFlux;
        if (year != null && month != null) {
            rankFlux = dcaRankRepository.findByRankingYearAndRankingMonthOrderByRankPositionAsc(year, month);
        } else {
            rankFlux = dcaRankRepository.findAll()
                    .collectList()
                    .flatMapMany(list -> {
                        if (list.isEmpty()) return Flux.empty();
                        Optional<DcaPopularityRank> maxOpt = list.stream()
                                .max(Comparator.comparingInt(DcaPopularityRank::getRankingYear)
                                        .thenComparingInt(DcaPopularityRank::getRankingMonth));
                        if (maxOpt.isEmpty()) return Flux.empty();
                        int latestY = maxOpt.get().getRankingYear();
                        int latestM = maxOpt.get().getRankingMonth();
                        return Flux.fromIterable(list.stream()
                                .filter(r -> r.getRankingYear() == latestY && r.getRankingMonth() == latestM)
                                .sorted(Comparator.comparingInt(DcaPopularityRank::getRankPosition))
                                .limit(20)
                                .toList());
                    });
        }

        return rankFlux.flatMapSequential(rank -> {
            if (rank == null || rank.getTicker() == null) {
                return Mono.justOrEmpty(rank);
            }
            Mono<GlobalAssetMetadata> metaMono = Mono.empty();
            if (metadataRepository != null) {
                try {
                    var res = metadataRepository.findByTicker(rank.getTicker());
                    if (res != null) metaMono = res;
                } catch (Exception ignored) {}
            }
            metaMono = metaMono.defaultIfEmpty(new GlobalAssetMetadata());

            Mono<List<DividendAnnouncement>> divsMono = Mono.just(Collections.emptyList());
            if (dividendRepository != null) {
                try {
                    var res = dividendRepository.findByTickerOrderByExDateDesc(rank.getTicker());
                    if (res != null) divsMono = res.collectList();
                } catch (Exception ignored) {}
            }

            return Mono.zip(metaMono, divsMono)
                    .map(tuple -> {
                        GlobalAssetMetadata meta = tuple.getT1();
                        List<DividendAnnouncement> divs = tuple.getT2();
                        if (meta != null && meta.getName() != null) {
                            rank.setName(meta.getName());
                        }
                        LocalDateTime listingDate = (meta != null) ? meta.getListingDate() : null;
                        DistributionFrequency freq = GlobalAssetScoreEvaluationService.deriveDistributionFrequency(
                                divs, listingDate, LocalDateTime.now());
                        rank.setDistributionFrequency(freq);
                        return rank;
                    });
        });
    }

    public Flux<DataFeedSyncWatermark> listDataFeedWatermarks() {
        return watermarkRepository != null ? watermarkRepository.findAll() : Flux.empty();
    }

    private Mono<GlobalAssetScore> enrichScore(GlobalAssetScore score) {
        if (score == null || score.getTicker() == null) {
            return Mono.justOrEmpty(score);
        }
        String ticker = score.getTicker();

        Mono<GlobalAssetMetadata> metaMono = Mono.empty();
        if (metadataRepository != null) {
            try {
                var res = metadataRepository.findByTicker(ticker);
                if (res != null) metaMono = res;
            } catch (Exception ignored) {}
        }
        metaMono = metaMono.defaultIfEmpty(new GlobalAssetMetadata());

        Mono<List<MarketDailyQuote>> quotesMono = Mono.just(Collections.emptyList());
        if (quoteRepository != null) {
            try {
                LocalDateTime anchor = (score.getEvaluationDate() != null) ? score.getEvaluationDate() : LocalDateTime.now();
                LocalDateTime startDate = anchor.minusMonths(13);
                var res = quoteRepository.findByTickerAndTradeDateGreaterThanEqualOrderByTradeDateDesc(ticker, startDate);
                if (res != null) quotesMono = res.collectList();
            } catch (Exception ignored) {}
        }

        Mono<List<DividendAnnouncement>> divsMono = Mono.just(Collections.emptyList());
        if (dividendRepository != null) {
            try {
                var res = dividendRepository.findByTickerOrderByExDateDesc(ticker);
                if (res != null) divsMono = res.collectList();
            } catch (Exception ignored) {}
        }

        return Mono.zip(metaMono, quotesMono, divsMono)
                .map(tuple -> {
                    GlobalAssetMetadata meta = tuple.getT1();
                    List<MarketDailyQuote> quotes = tuple.getT2();
                    List<DividendAnnouncement> divs = tuple.getT3();

                    if (meta != null && meta.getName() != null) {
                        score.setName(meta.getName());
                    }

                    LocalDateTime listingDate = (meta != null) ? meta.getListingDate() : null;
                    LocalDateTime now = LocalDateTime.now();
                    DistributionFrequency freq = GlobalAssetScoreEvaluationService.deriveDistributionFrequency(
                            divs, listingDate, now);
                    score.setDistributionFrequency(freq);

                    if (quotes != null && !quotes.isEmpty()) {
                        MarketDailyQuote latest = quotes.get(0);
                        score.setClosePrice(latest.getClosePrice());

                        if (quotes.size() >= 2) {
                            MarketDailyQuote prev = quotes.get(1);
                            if (prev.getClosePrice() != null && prev.getClosePrice().compareTo(BigDecimal.ZERO) > 0
                                    && latest.getClosePrice() != null) {
                                BigDecimal diff = latest.getClosePrice().subtract(prev.getClosePrice());
                                BigDecimal pct = diff.divide(prev.getClosePrice(), 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP);
                                score.setChangePct(pct);
                            }
                        }

                        score.setReturn1m(calculatePeriodReturn(quotes, divs, latest, Period.ofMonths(1)));
                        score.setReturn3m(calculatePeriodReturn(quotes, divs, latest, Period.ofMonths(3)));
                        score.setReturn6m(calculatePeriodReturn(quotes, divs, latest, Period.ofMonths(6)));
                        score.setReturn1y(calculatePeriodReturn(quotes, divs, latest, Period.ofYears(1)));
                    }

                    return score;
                });
    }

    private BigDecimal calculatePeriodReturn(List<MarketDailyQuote> quotes, List<DividendAnnouncement> divs, MarketDailyQuote latest, Period period) {
        if (quotes == null || quotes.isEmpty() || latest == null || latest.getClosePrice() == null || latest.getTradeDate() == null) {
            return null;
        }
        LocalDateTime targetDate = latest.getTradeDate().minus(period);

        MarketDailyQuote past = null;
        for (MarketDailyQuote q : quotes) {
            if (q != null && q.getTradeDate() != null && !q.getTradeDate().isAfter(targetDate)) {
                past = q;
                break;
            }
        }
        if (past == null || past.getClosePrice() == null || past.getClosePrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal pLatest = latest.getClosePrice();
        BigDecimal pPast = past.getClosePrice();

        LocalDateTime startDate = past.getTradeDate();
        LocalDateTime endDate = latest.getTradeDate();

        BigDecimal divSum = BigDecimal.ZERO;
        if (divs != null) {
            for (DividendAnnouncement d : divs) {
                if (d != null && d.getExDate() != null && !d.getExDate().isBefore(startDate) && !d.getExDate().isAfter(endDate)) {
                    if (d.getDividendPerShare() != null) {
                        divSum = divSum.add(d.getDividendPerShare());
                    }
                }
            }
        }

        BigDecimal totalGain = pLatest.subtract(pPast).add(divSum);
        return totalGain.divide(pPast, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Flux<DcaPopularityRank> listDcaRanksByAssetId(UUID assetId) {
        return dcaRankRepository.findByAssetIdOrderByRankingYearDescRankingMonthDesc(assetId);
    }

    public Flux<DividendAnnouncement> listDividendAnnouncements(DividendAnnouncementFilterInput filter) {
        if (filter == null) {
            return dividendRepository.findAll();
        }
        if (filter.ticker() != null && filter.startDate() != null && filter.endDate() != null) {
            LocalDateTime start = parseDate(filter.startDate(), false);
            LocalDateTime end = parseDate(filter.endDate(), true);
            return dividendRepository.findByTickerAndExDateBetweenOrderByExDateAsc(filter.ticker(), start, end);
        }
        if (filter.ticker() != null) {
            return dividendRepository.findByTickerOrderByExDateDesc(filter.ticker());
        }
        return dividendRepository.findAll();
    }

    public Mono<DividendAnnouncement> getDividendAnnouncementById(UUID id) {
        return dividendRepository.findById(id);
    }

    public Flux<DividendAnnouncement> getUpcomingDividends(String startDate, String endDate) {
        LocalDateTime start = parseDate(startDate, false);
        LocalDateTime end = parseDate(endDate, true);
        return dividendRepository.findByExDateBetweenOrderByExDateAsc(start, end);
    }

    public Flux<DividendAnnouncement> listDividendsByAssetId(UUID assetId) {
        return dividendRepository.findByAssetIdOrderByExDateDesc(assetId);
    }

    public Flux<CorporateAction> listCorporateActions(CorporateActionFilterInput filter) {
        if (filter != null && filter.ticker() != null) {
            return corporateActionRepository.findByTickerOrderByEffectiveDateDesc(filter.ticker());
        }
        return corporateActionRepository.findAll();
    }

    public Mono<CorporateAction> getCorporateActionById(UUID id) {
        return corporateActionRepository.findById(id);
    }

    public Flux<CorporateAction> getEffectiveSplits(String ticker, String effectiveDate) {
        LocalDateTime date = parseDate(effectiveDate, false);
        return corporateActionRepository.findByTickerAndEffectiveDate(ticker, date).flux();
    }

    public Flux<CorporateAction> listCorporateActionsByAssetId(UUID assetId) {
        return corporateActionRepository.findByAssetIdOrderByEffectiveDateDesc(assetId);
    }

    private LocalDateTime parseDate(String dateStr, boolean endOfDay) {
        try {
            if (dateStr.length() == 10) {
                LocalDate d = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
            }
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse date '{}', defaulting to now: {}", dateStr, e.getMessage());
            return LocalDateTime.now();
        }
    }
}
