package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.dto.SyncedRecordsCount;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.model.SyncScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alphaharvester.domain.entity.DataFeedSyncWatermark;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class MarketDataSyncService implements MarketDataSyncUseCase {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncService.class);

    public static final String WATERMARK_TAIWAN_ETF_QUOTES = "TAIWAN_ETF_QUOTES";
    public static final String WATERMARK_GLOBAL_BENCHMARKS = "GLOBAL_BENCHMARKS";
    public static final String WATERMARK_CNN_FEAR_GREED = "CNN_FEAR_GREED";
    public static final String WATERMARK_MACRO_YIELD_SNAPSHOT = "MACRO_YIELD_SNAPSHOT";
    public static final String WATERMARK_TWSE_DCA_RANKINGS = "TWSE_DCA_RANKINGS";
    public static final String WATERMARK_TWSE_ETF_METADATA = "TWSE_ETF_METADATA";

    private final ExternalMarketDataPort externalMarketDataPort;
    private final GlobalAssetMetadataRepository metadataRepository;
    private final BenchmarkIndexRepository benchmarkRepository;
    private final MarketDailyQuoteRepository quoteRepository;
    private final MacroYieldSnapshotRepository macroYieldRepository;
    private final DcaPopularityRankRepository dcaRankRepository;
    private final DividendAnnouncementRepository dividendRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final GlobalAssetScoreEvaluationService scoreEvaluationService;
    private final DataFeedSyncWatermarkRepository watermarkRepository;
    private final DataCompletenessGatekeeperService gatekeeperService;

    @Autowired
    public MarketDataSyncService(ExternalMarketDataPort externalMarketDataPort,
                                 GlobalAssetMetadataRepository metadataRepository,
                                 BenchmarkIndexRepository benchmarkRepository,
                                 MarketDailyQuoteRepository quoteRepository,
                                 MacroYieldSnapshotRepository macroYieldRepository,
                                 DcaPopularityRankRepository dcaRankRepository,
                                 DividendAnnouncementRepository dividendRepository,
                                 CorporateActionRepository corporateActionRepository,
                                 GlobalAssetScoreEvaluationService scoreEvaluationService,
                                 DataFeedSyncWatermarkRepository watermarkRepository,
                                 DataCompletenessGatekeeperService gatekeeperService) {
        this.externalMarketDataPort = externalMarketDataPort;
        this.metadataRepository = metadataRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.quoteRepository = quoteRepository;
        this.macroYieldRepository = macroYieldRepository;
        this.dcaRankRepository = dcaRankRepository;
        this.dividendRepository = dividendRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.scoreEvaluationService = scoreEvaluationService;
        this.watermarkRepository = watermarkRepository;
        this.gatekeeperService = gatekeeperService;
    }

    public MarketDataSyncService(ExternalMarketDataPort externalMarketDataPort,
                                 GlobalAssetMetadataRepository metadataRepository,
                                 BenchmarkIndexRepository benchmarkRepository,
                                 MarketDailyQuoteRepository quoteRepository,
                                 MacroYieldSnapshotRepository macroYieldRepository,
                                 DcaPopularityRankRepository dcaRankRepository,
                                 DividendAnnouncementRepository dividendRepository,
                                 CorporateActionRepository corporateActionRepository,
                                 GlobalAssetScoreEvaluationService scoreEvaluationService,
                                 DataFeedSyncWatermarkRepository watermarkRepository) {
        this(externalMarketDataPort, metadataRepository, benchmarkRepository, quoteRepository,
             macroYieldRepository, dcaRankRepository, dividendRepository, corporateActionRepository,
             scoreEvaluationService, watermarkRepository, null);
    }

    @Override
    @Transactional
    public Mono<MarketDataSyncResponse> syncMarketData(MarketDataSyncRequest request) {
        SyncScope scope = request.scope() != null ? request.scope() : SyncScope.ALL;
        LocalDateTime now = LocalDateTime.now();
        log.info("Initiating market data synchronization pipeline. Scope: {}, ExecutedAt: {}", scope, now);

        return executeSync(scope, now, request.backfillDays())
                .flatMap(counts -> {
                    if (gatekeeperService != null) {
                        return gatekeeperService.checkCompleteness()
                                .flatMap(report -> {
                                    if (!report.isPassed()) {
                                        log.warn("Gatekeeper HALTED pipeline! Violations: {}", report.violations());
                                        return Mono.just(new MarketDataSyncResponse(
                                                "HALT",
                                                "市場數據採集未齊全，量化引擎已安全暫停：" + String.join("; ", report.violations()),
                                                now.toString(),
                                                counts,
                                                report
                                        ));
                                    }
                                    log.info("Gatekeeper PASSED. All data completeness checks cleared.");
                                    if (Boolean.TRUE.equals(request.evaluateAfterSync())) {
                                        log.info("Triggering post-sync candidate multi-factor evaluation...");
                                        return scoreEvaluationService.evaluateGlobalAssetScores()
                                                .thenReturn(new MarketDataSyncResponse(
                                                        "SUCCESS",
                                                        "Market data synchronization and factor evaluation completed successfully.",
                                                        now.toString(),
                                                        counts,
                                                        report
                                                ));
                                    }
                                    return Mono.just(new MarketDataSyncResponse(
                                            "SUCCESS",
                                            "Market data synchronization completed successfully.",
                                            now.toString(),
                                            counts,
                                            report
                                    ));
                                });
                    }

                    if (Boolean.TRUE.equals(request.evaluateAfterSync())) {
                        log.info("Triggering post-sync candidate multi-factor evaluation...");
                        return scoreEvaluationService.evaluateGlobalAssetScores()
                                .thenReturn(new MarketDataSyncResponse(
                                        "SUCCESS",
                                        "Market data synchronization completed successfully.",
                                        now.toString(),
                                        counts,
                                        null
                                ));
                    }
                    return Mono.just(new MarketDataSyncResponse(
                            "SUCCESS",
                            "Market data synchronization completed successfully.",
                            now.toString(),
                            counts,
                            null
                    ));
                })
                .doOnError(e -> log.error("Market data synchronization pipeline failed: {}", e.getMessage(), e));
    }

    private Mono<SyncedRecordsCount> executeSync(SyncScope scope, LocalDateTime now, Integer backfillDays) {
        return syncMetadata(scope, now)
                .flatMap(metaCount -> syncQuotes(scope, now, backfillDays)
                        .flatMap(quotesCount -> syncMacroYields(scope, now)
                                .flatMap(yieldCount -> syncDcaRanks(scope, now)
                                        .flatMap(dcaCount -> syncDividendsAndSplits(scope, now)
                                                .map(divSplits -> new SyncedRecordsCount(
                                                        metaCount,
                                                        quotesCount,
                                                        yieldCount,
                                                        dcaCount,
                                                        divSplits[0],
                                                        divSplits[1]
                                                ))))));
    }

    private Mono<Integer> syncMetadata(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.METADATA) {
            return Mono.just(0);
        }
        log.info("Fetching and syncing ETF metadata catalog from TWSE OpenAPI...");
        return watermarkRepository.findByFeedName(WATERMARK_TWSE_ETF_METADATA)
                .map(DataFeedSyncWatermark::getLatestRecordDate)
                .defaultIfEmpty(now.minusDays(1))
                .flatMap(latestRecordDate -> externalMarketDataPort.fetchEtfMasterUniverse()
                        .flatMap(asset -> metadataRepository.findByTicker(asset.getTicker())
                                .flatMap(existing -> {
                                    existing.setName(asset.getName());
                                    existing.setTotalExpenseRatio(asset.getTotalExpenseRatio());
                                    existing.setFundSizeTwd(asset.getFundSizeTwd());
                                    existing.setAssetClass(asset.getAssetClass());
                                    existing.setDistributionFrequency(asset.getDistributionFrequency());
                                    existing.setUpdatedAt(now);
                                    return metadataRepository.save(existing);
                                })
                                .switchIfEmpty(metadataRepository.save(asset)))
                        .count()
                        .map(Long::intValue)
                        .flatMap(metaCount -> updateWatermark(WATERMARK_TWSE_ETF_METADATA, now, now, metaCount)
                                .thenReturn(metaCount)));
    }

    private Mono<Integer> syncQuotes(SyncScope scope, LocalDateTime now, Integer backfillDays) {
        if (scope != SyncScope.ALL && scope != SyncScope.QUOTES) {
            return Mono.just(0);
        }
        log.info("Syncing 3 distinct market quote categories (Taiwan ETFs, Global Benchmarks, CNN Sentiment)...");
        return syncTaiwanEtfQuotes(now, backfillDays)
                .flatMap(etfCount -> syncBenchmarkQuotes(now, backfillDays)
                        .flatMap(benchCount -> syncCnnSentiment(now, backfillDays)
                                .map(cnnCount -> {
                                    int total = etfCount + benchCount + cnnCount;
                                    log.info("Completed market quote synchronization: {} ETFs, {} Benchmarks, {} CNN sentiment (Total: {})",
                                            etfCount, benchCount, cnnCount, total);
                                    return total;
                                })));
    }

    private Mono<Integer> syncTaiwanEtfQuotes(LocalDateTime now, Integer backfillDays) {
        return watermarkRepository.findByFeedName(WATERMARK_TAIWAN_ETF_QUOTES)
                .map(DataFeedSyncWatermark::getLatestRecordDate)
                .defaultIfEmpty(now.minusDays(1))
                .flatMap(latestRecordDate -> {
                    long daysMissed = ChronoUnit.DAYS.between(latestRecordDate.toLocalDate(), now.toLocalDate());
                    long allowedGap = (now.getDayOfWeek() == DayOfWeek.MONDAY) ? 3 : 1;
                    boolean hasGap = daysMissed > allowedGap;
                    boolean force = backfillDays != null && backfillDays > 0;

                    log.info("Watermark comparison for '{}': latestRecordDate={}, today={}, daysMissed={}, allowedGap={}, hasGap={}, force={}",
                            WATERMARK_TAIWAN_ETF_QUOTES, latestRecordDate, now, daysMissed, allowedGap, hasGap, force);

                    Mono<Integer> primaryCountMono = metadataRepository.findAll()
                            .map(GlobalAssetMetadata::getTicker)
                            .collectList()
                            .flatMapMany(externalMarketDataPort::fetchTaiwanEtfDailyQuotes)
                            .flatMap(this::upsertDailyQuote)
                            .count()
                            .map(Long::intValue);

                    Mono<Integer> backfillCountMono;
                    if (hasGap || force) {
                        int effectiveDays = force ? backfillDays : (int) Math.max(daysMissed, 5);
                        String range = deriveRange(effectiveDays);
                        log.warn("Watermark confirmed ETF trading gap! Missed {} days. Backfilling candidate ETF universe from Yahoo Finance (range: '{}')...",
                                daysMissed, range);

                        backfillCountMono = metadataRepository.findAll()
                                .map(GlobalAssetMetadata::getTicker)
                                .collectList()
                                .flatMapMany(tickers -> Flux.fromIterable(tickers)
                                        .flatMap(ticker -> externalMarketDataPort.fetchHistoricalQuotes(ticker, range), 4)
                                        .flatMap(this::upsertDailyQuote))
                                .count()
                                .map(Long::intValue);
                    } else {
                        backfillCountMono = Mono.just(0);
                    }

                    return primaryCountMono.flatMap(primaryCount ->
                            backfillCountMono.flatMap(backfillCount -> {
                                int total = primaryCount + backfillCount;
                                return updateWatermark(WATERMARK_TAIWAN_ETF_QUOTES, now, now, total)
                                        .thenReturn(total);
                            })
                    );
                });
    }

    private Mono<Integer> syncBenchmarkQuotes(LocalDateTime now, Integer backfillDays) {
        return watermarkRepository.findByFeedName(WATERMARK_GLOBAL_BENCHMARKS)
                .map(DataFeedSyncWatermark::getLatestRecordDate)
                .defaultIfEmpty(now.minusDays(1))
                .flatMap(latestRecordDate -> {
                    long daysMissed = ChronoUnit.DAYS.between(latestRecordDate.toLocalDate(), now.toLocalDate());
                    long allowedGap = (now.getDayOfWeek() == DayOfWeek.MONDAY) ? 3 : 1;
                    boolean hasGap = daysMissed > allowedGap;
                    boolean force = backfillDays != null && backfillDays > 0;

                    int effectiveDays = force ? backfillDays : (int) Math.max(daysMissed, 5);
                    String range = (hasGap || force) ? deriveRange(effectiveDays) : "5d";

                    log.info("Watermark comparison for '{}': latestRecordDate={}, today={}, daysMissed={}, range='{}'",
                            WATERMARK_GLOBAL_BENCHMARKS, latestRecordDate, now, daysMissed, range);

                    return externalMarketDataPort.fetchBenchmarkQuotes(range)
                            .flatMap(this::upsertDailyQuote)
                            .count()
                            .map(Long::intValue)
                            .flatMap(count -> updateWatermark(WATERMARK_GLOBAL_BENCHMARKS, now, now, count)
                                    .thenReturn(count));
                });
    }

    private Mono<Integer> syncCnnSentiment(LocalDateTime now, Integer backfillDays) {
        return watermarkRepository.findByFeedName(WATERMARK_CNN_FEAR_GREED)
                .map(DataFeedSyncWatermark::getLatestRecordDate)
                .defaultIfEmpty(now.minusDays(1))
                .flatMap(latestRecordDate -> {
                    log.info("Syncing CNN Fear & Greed Sentiment (Watermark: '{}', latestRecordDate={})...",
                            WATERMARK_CNN_FEAR_GREED, latestRecordDate);

                    return externalMarketDataPort.fetchCnnSentimentQuote()
                            .flatMap(this::upsertDailyQuote)
                            .map(q -> 1)
                            .defaultIfEmpty(0)
                            .flatMap(count -> updateWatermark(WATERMARK_CNN_FEAR_GREED, now, now, count)
                                    .thenReturn(count));
                });
    }

    private Mono<Void> updateWatermark(String feedName, LocalDateTime syncTime, LocalDateTime recordDate, int recordsCount) {
        return watermarkRepository.findByFeedName(feedName)
                .flatMap(wm -> {
                    wm.setLastSuccessfulSyncAt(syncTime);
                    wm.setLatestRecordDate(recordDate);
                    wm.setRecordsSyncedCount(recordsCount);
                    wm.setStatus("SUCCESS");
                    wm.setErrorMessage(null);
                    wm.setUpdatedAt(syncTime);
                    return watermarkRepository.save(wm);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    DataFeedSyncWatermark newWm = new DataFeedSyncWatermark(
                            UUID.randomUUID(), feedName, syncTime, recordDate, recordsCount, "SUCCESS", null, syncTime
                    );
                    return watermarkRepository.save(newWm);
                }))
                .then();
    }

    private Mono<MarketDailyQuote> upsertDailyQuote(MarketDailyQuote q) {
        return quoteRepository.findByTickerAndTradeDate(q.getTicker(), q.getTradeDate())
                .flatMap(existing -> {
                    existing.setOpenPrice(q.getOpenPrice());
                    existing.setHighPrice(q.getHighPrice());
                    existing.setLowPrice(q.getLowPrice());
                    existing.setClosePrice(q.getClosePrice());
                    existing.setVolumeShares(q.getVolumeShares());
                    existing.setTradeValueTwd(q.getTradeValueTwd());
                    existing.setNetAssetValue(q.getNetAssetValue());
                    existing.setDiscountPremiumPercentage(q.getDiscountPremiumPercentage());
                    return quoteRepository.save(existing);
                })
                .switchIfEmpty(quoteRepository.save(q));
    }

    private boolean isTradingGap(LocalDateTime latestTradeDate, LocalDateTime now) {
        if (latestTradeDate == null) {
            return true;
        }
        long daysBetween = ChronoUnit.DAYS.between(latestTradeDate.toLocalDate(), now.toLocalDate());
        long allowedGap = now.getDayOfWeek() == DayOfWeek.MONDAY ? 3 : 1;
        return daysBetween > allowedGap;
    }

    private String deriveRange(int days) {
        if (days <= 30) return "1mo";
        if (days <= 90) return "3mo";
        if (days <= 180) return "6mo";
        if (days <= 365) return "1y";
        return "2y";
    }

    private Mono<Integer> syncMacroYields(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.MACRO_YIELDS) {
            return Mono.just(0);
        }
        log.info("Fetching and syncing macroeconomic treasury yields from Yahoo Finance...");
        return watermarkRepository.findByFeedName(WATERMARK_MACRO_YIELD_SNAPSHOT)
                .map(DataFeedSyncWatermark::getLatestRecordDate)
                .defaultIfEmpty(now.minusDays(1))
                .flatMap(latestRecordDate -> externalMarketDataPort.fetchLatestMacroYield()
                        .flatMap(snapshot -> macroYieldRepository.findByRecordDate(snapshot.getRecordDate())
                                .flatMap(existing -> {
                                    existing.setUsCorporateBondEffectiveYield(snapshot.getUsCorporateBondEffectiveYield());
                                    existing.setUs10YearTreasuryYield(snapshot.getUs10YearTreasuryYield());
                                    existing.setUs20YearTreasuryYield(snapshot.getUs20YearTreasuryYield());
                                    existing.setYieldSpread10yMinus2y(snapshot.getYieldSpread10yMinus2y());
                                    return macroYieldRepository.save(existing);
                                })
                                .switchIfEmpty(macroYieldRepository.save(snapshot))
                                .flatMap(saved -> updateWatermark(WATERMARK_MACRO_YIELD_SNAPSHOT, now, saved.getRecordDate(), 1)
                                        .thenReturn(1)))
                        .defaultIfEmpty(0));
    }

    private Mono<Integer> syncDcaRanks(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.DCA_RANKS) {
            return Mono.just(0);
        }
        log.info("Fetching and syncing regular quota (DCA) Top 20 rankings from TWSE...");
        return watermarkRepository.findByFeedName(WATERMARK_TWSE_DCA_RANKINGS)
                .map(DataFeedSyncWatermark::getLatestRecordDate)
                .defaultIfEmpty(now.minusMonths(1))
                .flatMap(latestRecordDate -> externalMarketDataPort.fetchDcaPopularityRanks(now.getYear(), now.getMonthValue())
                        .flatMap(rank -> metadataRepository.findByTicker(rank.getTicker())
                                .flatMap(asset -> {
                                    rank.setAssetId(asset.getId());
                                    return dcaRankRepository.findByTickerAndRankingYearAndRankingMonth(
                                                    rank.getTicker(), rank.getRankingYear(), rank.getRankingMonth())
                                            .flatMap(existing -> {
                                                existing.setRankPosition(rank.getRankPosition());
                                                existing.setRegularInvestorCount(rank.getRegularInvestorCount());
                                                existing.setAssetId(asset.getId());
                                                return dcaRankRepository.save(existing);
                                            })
                                            .switchIfEmpty(dcaRankRepository.save(rank));
                                })
                                .switchIfEmpty(
                                        dcaRankRepository.findByTickerAndRankingYearAndRankingMonth(
                                                        rank.getTicker(), rank.getRankingYear(), rank.getRankingMonth())
                                                .flatMap(existing -> {
                                                    existing.setRankPosition(rank.getRankPosition());
                                                    existing.setRegularInvestorCount(rank.getRegularInvestorCount());
                                                    return dcaRankRepository.save(existing);
                                                })
                                                .switchIfEmpty(dcaRankRepository.save(rank))
                                ))
                        .count()
                        .map(Long::intValue)
                        .flatMap(dcaCount -> updateWatermark(WATERMARK_TWSE_DCA_RANKINGS, now, now, dcaCount)
                                .thenReturn(dcaCount)));
    }

    private Mono<int[]> syncDividendsAndSplits(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.DIVIDENDS_AND_SPLITS) {
            return Mono.just(new int[]{0, 0});
        }
        log.info("Fetching and syncing dividend announcements and stock splits...");
        return metadataRepository.findAll()
                .flatMap(asset -> {
                    Mono<Integer> divCount = externalMarketDataPort.fetchDividendAnnouncements(asset.getTicker())
                            .flatMap(div -> {
                                div.setAssetId(asset.getId());
                                return dividendRepository.findByTickerAndExDate(div.getTicker(), div.getExDate())
                                        .flatMap(existing -> {
                                            existing.setDividendPerShare(div.getDividendPerShare());
                                            existing.setPaymentDate(div.getPaymentDate());
                                            existing.setTaxTag(div.getTaxTag());
                                            existing.setAssetId(asset.getId());
                                            return dividendRepository.save(existing);
                                        })
                                        .switchIfEmpty(dividendRepository.save(div));
                            })
                            .count()
                            .map(Long::intValue);

                    Mono<Integer> splitCount = externalMarketDataPort.fetchCorporateActions(asset.getTicker())
                            .flatMap(split -> {
                                split.setAssetId(asset.getId());
                                return corporateActionRepository.findByTickerAndEffectiveDate(split.getTicker(), split.getEffectiveDate())
                                        .flatMap(existing -> {
                                            existing.setSplitToShares(split.getSplitToShares());
                                            existing.setSplitFromShares(split.getSplitFromShares());
                                            existing.setAssetId(asset.getId());
                                            return corporateActionRepository.save(existing);
                                        })
                                        .switchIfEmpty(corporateActionRepository.save(split));
                            })
                            .count()
                            .map(Long::intValue);

                    return Mono.zip(divCount, splitCount, (d, s) -> new int[]{d, s});
                })
                .reduce(new int[]{0, 0}, (acc, cur) -> new int[]{acc[0] + cur[0], acc[1] + cur[1]});
    }
}
