package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.*;
import com.alphaharvester.domain.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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

    public GlobalAssetQueryService(GlobalAssetMetadataRepository metadataRepository,
                                   BenchmarkIndexRepository benchmarkRepository,
                                   MarketDailyQuoteRepository quoteRepository,
                                   MacroYieldSnapshotRepository macroYieldRepository,
                                   GlobalAssetScoreRepository scoreRepository,
                                   DcaPopularityRankRepository dcaRankRepository,
                                   DividendAnnouncementRepository dividendRepository,
                                   CorporateActionRepository corporateActionRepository) {
        this.metadataRepository = metadataRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.quoteRepository = quoteRepository;
        this.macroYieldRepository = macroYieldRepository;
        this.scoreRepository = scoreRepository;
        this.dcaRankRepository = dcaRankRepository;
        this.dividendRepository = dividendRepository;
        this.corporateActionRepository = corporateActionRepository;
    }

    public Flux<GlobalAssetMetadata> listGlobalAssets(GlobalAssetFilterInput filter) {
        if (filter == null || (filter.ticker() == null && filter.assetClass() == null && filter.distributionFrequency() == null)) {
            return metadataRepository.findAll();
        }
        GlobalAssetMetadata probe = new GlobalAssetMetadata();
        probe.setTicker(filter.ticker());
        probe.setAssetClass(filter.assetClass());
        probe.setDistributionFrequency(filter.distributionFrequency());

        ExampleMatcher matcher = ExampleMatcher.matchingAll()
                .withIgnoreNullValues()
                .withIgnorePaths("version", "totalExpenseRatio", "fundSizeTwd");

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
        return quoteRepository.findByAssetIdOrderByTradeDateAsc(assetId);
    }

    public Flux<MarketDailyQuote> listQuotesByBenchmarkId(UUID benchmarkId) {
        return quoteRepository.findByBenchmarkIdOrderByTradeDateAsc(benchmarkId);
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
                .withIgnorePaths("compositeScore", "totalExpenseRatio", "fundSizeTwd", "classRank");

        return scoreRepository.findAll(Example.of(probe, matcher));
    }

    public Mono<GlobalAssetScore> getGlobalAssetScoreById(UUID id) {
        return scoreRepository.findById(id);
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
        return dcaRankRepository.findByRankingYearAndRankingMonthOrderByRankPositionAsc(year, month);
    }

    public Flux<DcaPopularityRank> listDcaRanksByAssetId(UUID assetId) {
        return dcaRankRepository.findByAssetIdOrderByRankingYearDescRankingMonthDesc(assetId);
    }

    public Flux<DividendAnnouncement> listDividendAnnouncements(DividendAnnouncementFilterInput filter) {
        if (filter != null && filter.startDate() != null && filter.endDate() != null) {
            LocalDateTime start = parseDate(filter.startDate(), false);
            LocalDateTime end = parseDate(filter.endDate(), true);
            return dividendRepository.findByExDateBetweenOrderByExDateAsc(start, end);
        }
        if (filter != null && filter.ticker() != null) {
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

