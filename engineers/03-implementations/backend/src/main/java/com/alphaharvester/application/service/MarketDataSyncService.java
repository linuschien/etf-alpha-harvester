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
import com.alphaharvester.domain.entity.DcaPopularityRank;
import com.alphaharvester.domain.entity.DividendAnnouncement;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    public static final String WATERMARK_DIVIDENDS_AND_SPLITS = "DIVIDENDS_AND_SPLITS";

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

    @Override
    @Transactional
    public Mono<MarketDataSyncResponse> syncMarketData(MarketDataSyncRequest request) {
        SyncScope scope = request.scope() != null ? request.scope() : SyncScope.ALL;
        LocalDateTime now = LocalDateTime.now();
        log.info("Initiating market data synchronization pipeline. Scope: {}, ExecutedAt: {}", scope, now);

        return executeSync(scope, now, request.backfillDays())
                .flatMap(counts -> {
                    boolean requiresCompletenessCheck = gatekeeperService != null
                            && (Boolean.TRUE.equals(request.evaluateAfterSync()) || scope == SyncScope.ALL || scope == SyncScope.QUOTES);

                    if (requiresCompletenessCheck) {
                        return gatekeeperService.checkCompleteness()
                                .flatMap(report -> {
                                    if (!report.isPassed()) {
                                        log.warn("Gatekeeper HALTED pipeline! Violations: {}", report.violations());
                                        return markWatermarksHalt(now, report.message())
                                                .thenReturn(new MarketDataSyncResponse(
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
                        .flatMap(quotesCount -> syncMacroYields(scope, now, backfillDays)
                                .flatMap(yieldCount -> syncDcaRanks(scope, now)
                                        .flatMap(dcaCount -> syncDividendsAndSplits(scope, now, backfillDays)
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

        if (scope == SyncScope.ALL && watermarkRepository != null) {
            return watermarkRepository.findByFeedName(WATERMARK_TWSE_ETF_METADATA)
                    .flatMap(wm -> {
                        if ("SUCCESS".equalsIgnoreCase(wm.getStatus())
                                && wm.getRecordsSyncedCount() != null && wm.getRecordsSyncedCount() > 0
                                && wm.getLatestRecordDate() != null) {
                            if (wm.getLatestRecordDate().getYear() == now.getYear()
                                    && wm.getLatestRecordDate().getMonthValue() == now.getMonthValue()) {
                                log.info("TWSE ETF metadata for {}-{} has already been synced (Watermark SUCCESS, count={}). Skipping monthly sync.",
                                        now.getYear(), now.getMonthValue(), wm.getRecordsSyncedCount());
                                return Mono.just(0);
                            }
                        }
                        return executeSyncMetadata(now);
                    })
                    .switchIfEmpty(Mono.defer(() -> executeSyncMetadata(now)));
        }

        return executeSyncMetadata(now);
    }

    private Mono<Integer> executeSyncMetadata(LocalDateTime now) {
        log.info("Fetching and syncing ETF metadata catalog from TWSE OpenAPI...");
        return externalMarketDataPort.fetchEtfMasterUniverse()
                .flatMap(asset -> metadataRepository.findByTicker(asset.getTicker())
                        .flatMap(existing -> {
                            existing.setName(asset.getName());
                            if (asset.getUnderlyingIndex() != null) {
                                existing.setUnderlyingIndex(asset.getUnderlyingIndex());
                            }
                            if (asset.getListingDate() != null) {
                                existing.setListingDate(asset.getListingDate());
                            }
                            if (asset.getSharesOutstanding() != null) {
                                existing.setSharesOutstanding(asset.getSharesOutstanding());
                            }
                            if (asset.getNetAssetValue() != null) {
                                existing.setNetAssetValue(asset.getNetAssetValue());
                            }
                            if (asset.getFundSizeTwd() != null) {
                                existing.setFundSizeTwd(asset.getFundSizeTwd());
                            }
                            existing.setUpdatedAt(now);
                            return metadataRepository.save(existing);
                        })
                        .switchIfEmpty(Mono.defer(() -> metadataRepository.save(asset))))
                .count()
                .map(Long::intValue)
                .flatMap(metaCount -> {
                    if (metaCount > 0) {
                        return updateWatermarkSuccess(WATERMARK_TWSE_ETF_METADATA, now, now, metaCount)
                                .thenReturn(metaCount);
                    } else {
                        return updateWatermarkFailed(WATERMARK_TWSE_ETF_METADATA, now, "No ETF metadata records fetched")
                                .thenReturn(0);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync ETF metadata: {}", e.getMessage(), e);
                    return updateWatermarkFailed(WATERMARK_TWSE_ETF_METADATA, now, extractErrorMessage(e))
                            .thenReturn(0);
                });
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
                .defaultIfEmpty(new DataFeedSyncWatermark(UUID.randomUUID(), WATERMARK_TAIWAN_ETF_QUOTES, null, now.minusDays(1), 0, "PENDING", null, now))
                .flatMap(watermark -> {
                    LocalDateTime latestRecordDate = (watermark.getLatestRecordDate() != null)
                            ? watermark.getLatestRecordDate()
                            : now.minusDays(1);
                    long daysMissed = ChronoUnit.DAYS.between(latestRecordDate.toLocalDate(), now.toLocalDate());
                    long allowedGap = (now.getDayOfWeek() == DayOfWeek.MONDAY) ? 3 : 1;
                    boolean hasGap = daysMissed > allowedGap || "HALT".equalsIgnoreCase(watermark.getStatus()) || "FAILED".equalsIgnoreCase(watermark.getStatus());
                    boolean force = backfillDays != null && backfillDays > 0;

                    log.info("Watermark comparison for '{}': latestRecordDate={}, status={}, today={}, daysMissed={}, allowedGap={}, hasGap={}, force={}",
                            WATERMARK_TAIWAN_ETF_QUOTES, latestRecordDate, watermark.getStatus(), now, daysMissed, allowedGap, hasGap, force);

                    Flux<MarketDailyQuote> primaryFlux = metadataRepository.findAll()
                            .map(GlobalAssetMetadata::getTicker)
                            .collectList()
                            .flatMapMany(externalMarketDataPort::fetchTaiwanEtfDailyQuotes)
                            .flatMap(this::upsertDailyQuote);

                    Flux<MarketDailyQuote> backfillFlux;
                    if (hasGap || force) {
                        int effectiveDays = force ? backfillDays : (int) Math.max(daysMissed, 5);
                        String range = deriveRange(effectiveDays);
                        log.warn("Watermark confirmed ETF trading gap! Missed {} days. Backfilling candidate ETF universe from Yahoo Finance (range: '{}')...",
                                daysMissed, range);

                        backfillFlux = metadataRepository.findAll()
                                .map(GlobalAssetMetadata::getTicker)
                                .collectList()
                                .flatMapMany(tickers -> Flux.fromIterable(tickers)
                                        .flatMap(ticker -> externalMarketDataPort.fetchHistoricalQuotes(ticker, range), 4)
                                        .flatMap(this::upsertDailyQuote));
                    } else {
                        backfillFlux = Flux.empty();
                    }

                    return Flux.concat(primaryFlux, backfillFlux)
                            .collectList()
                            .flatMap(savedList -> {
                                int total = savedList.size();
                                if (total > 0) {
                                    Optional<LocalDateTime> maxDateOpt = savedList.stream()
                                            .map(MarketDailyQuote::getTradeDate)
                                            .filter(Objects::nonNull)
                                            .max(LocalDateTime::compareTo);
                                    if (maxDateOpt.isPresent()) {
                                        return updateWatermarkSuccess(WATERMARK_TAIWAN_ETF_QUOTES, now, maxDateOpt.get(), total)
                                                .thenReturn(total);
                                    } else {
                                        return updateWatermarkFailed(WATERMARK_TAIWAN_ETF_QUOTES, now, "Synced Taiwan ETF quotes have missing or null trade dates")
                                                .thenReturn(0);
                                    }
                                } else {
                                    return updateWatermarkFailed(WATERMARK_TAIWAN_ETF_QUOTES, now, "No Taiwan ETF quotes synced")
                                            .thenReturn(0);
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync Taiwan ETF quotes: {}", e.getMessage(), e);
                    return updateWatermarkFailed(WATERMARK_TAIWAN_ETF_QUOTES, now, extractErrorMessage(e))
                            .thenReturn(0);
                });
    }

    private Mono<Integer> syncBenchmarkQuotes(LocalDateTime now, Integer backfillDays) {
        return watermarkRepository.findByFeedName(WATERMARK_GLOBAL_BENCHMARKS)
                .defaultIfEmpty(new DataFeedSyncWatermark(UUID.randomUUID(), WATERMARK_GLOBAL_BENCHMARKS, null, now.minusDays(1), 0, "PENDING", null, now))
                .flatMap(watermark -> {
                    LocalDateTime latestRecordDate = (watermark.getLatestRecordDate() != null)
                            ? watermark.getLatestRecordDate()
                            : now.minusDays(1);
                    long daysMissed = ChronoUnit.DAYS.between(latestRecordDate.toLocalDate(), now.toLocalDate());
                    long allowedGap = (now.getDayOfWeek() == DayOfWeek.MONDAY) ? 3 : 1;
                    boolean hasGap = daysMissed > allowedGap || "HALT".equalsIgnoreCase(watermark.getStatus()) || "FAILED".equalsIgnoreCase(watermark.getStatus());
                    boolean force = backfillDays != null && backfillDays > 0;

                    int effectiveDays = force ? backfillDays : (int) Math.max(daysMissed, 5);
                    String range = (hasGap || force) ? deriveRange(effectiveDays) : "5d";

                    log.info("Watermark comparison for '{}': latestRecordDate={}, today={}, daysMissed={}, range='{}'",
                            WATERMARK_GLOBAL_BENCHMARKS, latestRecordDate, now, daysMissed, range);

                    return externalMarketDataPort.fetchBenchmarkQuotes(range)
                            .flatMap(this::upsertDailyQuote)
                            .collectList()
                            .flatMap(savedList -> {
                                int count = savedList.size();
                                if (count > 0) {
                                    Optional<LocalDateTime> maxDateOpt = savedList.stream()
                                            .map(MarketDailyQuote::getTradeDate)
                                            .filter(Objects::nonNull)
                                            .max(LocalDateTime::compareTo);
                                    if (maxDateOpt.isPresent()) {
                                        return updateWatermarkSuccess(WATERMARK_GLOBAL_BENCHMARKS, now, maxDateOpt.get(), count)
                                                .thenReturn(count);
                                    } else {
                                        return updateWatermarkFailed(WATERMARK_GLOBAL_BENCHMARKS, now, "Synced benchmark quotes have missing or null trade dates")
                                                .thenReturn(0);
                                    }
                                } else {
                                    return updateWatermarkFailed(WATERMARK_GLOBAL_BENCHMARKS, now, "No benchmark quotes synced")
                                            .thenReturn(0);
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync benchmark quotes: {}", e.getMessage(), e);
                    return updateWatermarkFailed(WATERMARK_GLOBAL_BENCHMARKS, now, extractErrorMessage(e))
                            .thenReturn(0);
                });
    }

    private Mono<Integer> syncCnnSentiment(LocalDateTime now, Integer backfillDays) {
        return watermarkRepository.findByFeedName(WATERMARK_CNN_FEAR_GREED)
                .defaultIfEmpty(new DataFeedSyncWatermark(UUID.randomUUID(), WATERMARK_CNN_FEAR_GREED, null, now.minusDays(1), 0, "PENDING", null, now))
                .flatMap(watermark -> {
                    LocalDateTime latestRecordDate = (watermark.getLatestRecordDate() != null)
                            ? watermark.getLatestRecordDate()
                            : now.minusDays(1);
                    long daysMissed = ChronoUnit.DAYS.between(latestRecordDate.toLocalDate(), now.toLocalDate());
                    long allowedGap = (now.getDayOfWeek() == DayOfWeek.MONDAY) ? 3 : 1;
                    boolean hasGap = daysMissed > allowedGap || "HALT".equalsIgnoreCase(watermark.getStatus()) || "FAILED".equalsIgnoreCase(watermark.getStatus());
                    boolean force = backfillDays != null && backfillDays > 0;

                    int effectiveDays = force ? backfillDays : (int) Math.max(daysMissed, 5);
                    String range = (hasGap || force) ? deriveRange(effectiveDays) : "5d";

                    log.info("Syncing CNN Fear & Greed Sentiment (Watermark: '{}', latestRecordDate={}, hasGap={}, force={}, range='{}')...",
                            WATERMARK_CNN_FEAR_GREED, latestRecordDate, hasGap, force, range);

                    Flux<MarketDailyQuote> quotesFlux = (hasGap || force)
                            ? externalMarketDataPort.fetchHistoricalQuotes("FEAR_GREED", range)
                            : externalMarketDataPort.fetchCnnSentimentQuote().flux();

                    return quotesFlux
                            .flatMap(this::upsertDailyQuote)
                            .collectList()
                            .flatMap(savedList -> {
                                int count = savedList.size();
                                if (count > 0) {
                                    Optional<LocalDateTime> maxDateOpt = savedList.stream()
                                            .map(MarketDailyQuote::getTradeDate)
                                            .filter(Objects::nonNull)
                                            .max(LocalDateTime::compareTo);
                                    if (maxDateOpt.isPresent()) {
                                        return updateWatermarkSuccess(WATERMARK_CNN_FEAR_GREED, now, maxDateOpt.get(), count)
                                                .thenReturn(count);
                                    } else {
                                        return updateWatermarkFailed(WATERMARK_CNN_FEAR_GREED, now, "Synced CNN sentiment quotes have missing or null trade dates")
                                                .thenReturn(0);
                                    }
                                } else {
                                    return updateWatermarkFailed(WATERMARK_CNN_FEAR_GREED, now, "No CNN sentiment quotes synced")
                                            .thenReturn(0);
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync CNN sentiment: {}", e.getMessage(), e);
                    return updateWatermarkFailed(WATERMARK_CNN_FEAR_GREED, now, extractErrorMessage(e))
                            .thenReturn(0);
                });
    }

    private Mono<Void> updateWatermarkSuccess(String feedName, LocalDateTime syncTime, LocalDateTime recordDate, int recordsCount) {
        if (recordsCount <= 0) {
            return updateWatermarkFailed(feedName, syncTime, "Synced records count is 0");
        }
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

    private Mono<Void> updateWatermarkFailed(String feedName, LocalDateTime syncTime, String reason) {
        return watermarkRepository.findByFeedName(feedName)
                .flatMap(wm -> {
                    wm.setStatus("FAILED");
                    wm.setErrorMessage(reason);
                    wm.setRecordsSyncedCount(0);
                    wm.setUpdatedAt(syncTime);
                    // Crucial: preserve wm.getLatestRecordDate() and wm.getLastSuccessfulSyncAt()
                    return watermarkRepository.save(wm);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    DataFeedSyncWatermark newWm = new DataFeedSyncWatermark(
                            UUID.randomUUID(), feedName, null, null, 0, "FAILED", reason, syncTime
                    );
                    return watermarkRepository.save(newWm);
                }))
                .then();
    }

    private String extractErrorMessage(Throwable e) {
        if (e == null) return "Unknown error";
        if (e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isBlank()) {
            return e.getMessage() + ": " + e.getCause().getMessage();
        }
        return e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
    }

    private Mono<Void> markWatermarksHalt(LocalDateTime syncTime, String reason) {
        return watermarkRepository.findByFeedName(WATERMARK_TAIWAN_ETF_QUOTES)
                .flatMap(wm -> {
                    wm.setStatus("HALT");
                    wm.setErrorMessage(reason);
                    wm.setUpdatedAt(syncTime);
                    return watermarkRepository.save(wm);
                })
                .then();
    }

    private Mono<MarketDailyQuote> upsertDailyQuote(MarketDailyQuote q) {
        if (q.getTradeDate() != null) {
            q.setTradeDate(q.getTradeDate().toLocalDate().atStartOfDay());
        }
        boolean isTaiwanEtf = q.getTicker() != null && !q.getTicker().startsWith("^") && !"FEAR_GREED".equalsIgnoreCase(q.getTicker());
        return quoteRepository.findByTickerAndTradeDate(q.getTicker(), q.getTradeDate())
                .flatMap(existing -> {
                    if (q.getOpenPrice() != null) existing.setOpenPrice(q.getOpenPrice());
                    if (q.getHighPrice() != null) existing.setHighPrice(q.getHighPrice());
                    if (q.getLowPrice() != null) existing.setLowPrice(q.getLowPrice());
                    if (q.getClosePrice() != null && q.getClosePrice().compareTo(BigDecimal.ZERO) > 0) {
                        existing.setClosePrice(q.getClosePrice());
                    }
                    existing.setVolumeShares(q.getVolumeShares());
                    existing.setTradeValueTwd(q.getTradeValueTwd());
                    return quoteRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (isTaiwanEtf && (q.getClosePrice() == null || q.getClosePrice().compareTo(BigDecimal.ZERO) <= 0)) {
                        return quoteRepository.findFirstByTickerOrderByTradeDateDesc(q.getTicker())
                                .flatMap(prev -> {
                                    q.setClosePrice(prev.getClosePrice());
                                    return quoteRepository.save(q);
                                })
                                .switchIfEmpty(quoteRepository.save(q));
                    }
                    return quoteRepository.save(q);
                }));
    }

    private String deriveRange(int days) {
        if (days <= 30) return "1mo";
        if (days <= 90) return "3mo";
        if (days <= 180) return "6mo";
        if (days <= 365) return "1y";
        if (days <= 730) return "2y";
        return "5y";
    }

    private Mono<Integer> syncMacroYields(SyncScope scope, LocalDateTime now, Integer backfillDays) {
        if (scope != SyncScope.ALL && scope != SyncScope.MACRO_YIELDS) {
            return Mono.just(0);
        }
        log.info("Fetching and syncing macroeconomic yields from FRED...");
        return watermarkRepository.findByFeedName(WATERMARK_MACRO_YIELD_SNAPSHOT)
                .defaultIfEmpty(new DataFeedSyncWatermark(UUID.randomUUID(), WATERMARK_MACRO_YIELD_SNAPSHOT, null, now.minusDays(1), 0, "PENDING", null, now))
                .flatMap(watermark -> {
                    LocalDateTime latestRecordDate = (watermark.getLatestRecordDate() != null)
                            ? watermark.getLatestRecordDate()
                            : now.minusDays(1);
                    long daysMissed = ChronoUnit.DAYS.between(latestRecordDate.toLocalDate(), now.toLocalDate());
                    long allowedGap = (now.getDayOfWeek() == DayOfWeek.MONDAY) ? 3 : 1;
                    boolean hasGap = daysMissed > allowedGap || "HALT".equalsIgnoreCase(watermark.getStatus()) || "FAILED".equalsIgnoreCase(watermark.getStatus());
                    boolean force = backfillDays != null && backfillDays > 0;

                    if (hasGap || force) {
                        int effectiveDays = force ? backfillDays : (int) Math.max(daysMissed, 5);
                        LocalDate startDate = now.toLocalDate().minusDays(effectiveDays);
                        LocalDate endDate = now.toLocalDate();
                        log.warn("Macro yield watermark indicates gap or backfill requested (missed {} days, backfillDays={}). Syncing historical FRED yields from {} to {}...",
                                daysMissed, backfillDays, startDate, endDate);

                        return externalMarketDataPort.fetchHistoricalMacroYields(startDate, endDate)
                                .flatMap(this::upsertMacroYieldSnapshot)
                                .collectList()
                                .flatMap(savedList -> {
                                    int count = savedList.size();
                                    if (count > 0) {
                                        Optional<LocalDateTime> maxDateOpt = savedList.stream()
                                                .map(MacroYieldSnapshot::getRecordDate)
                                                .filter(Objects::nonNull)
                                                .max(LocalDateTime::compareTo);
                                        if (maxDateOpt.isPresent()) {
                                            return updateWatermarkSuccess(WATERMARK_MACRO_YIELD_SNAPSHOT, now, maxDateOpt.get(), count)
                                                    .thenReturn(count);
                                        } else {
                                            return updateWatermarkFailed(WATERMARK_MACRO_YIELD_SNAPSHOT, now, "Synced macro yield snapshots have missing or null record dates")
                                                    .thenReturn(0);
                                        }
                                    } else {
                                        return updateWatermarkFailed(WATERMARK_MACRO_YIELD_SNAPSHOT, now, "No historical macro yields synced")
                                                .thenReturn(0);
                                    }
                                });
                    }

                    return externalMarketDataPort.fetchLatestMacroYield()
                            .flatMap(this::upsertMacroYieldSnapshot)
                            .flatMap(saved -> updateWatermarkSuccess(WATERMARK_MACRO_YIELD_SNAPSHOT, now, saved.getRecordDate(), 1)
                                    .thenReturn(1))
                            .switchIfEmpty(Mono.defer(() ->
                                    updateWatermarkFailed(WATERMARK_MACRO_YIELD_SNAPSHOT, now, "Latest macro yield snapshot unavailable")
                                            .thenReturn(0)
                            ));
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync macro yields: {}", e.getMessage(), e);
                    return updateWatermarkFailed(WATERMARK_MACRO_YIELD_SNAPSHOT, now, extractErrorMessage(e))
                            .thenReturn(0);
                });
    }

    private Mono<MacroYieldSnapshot> upsertMacroYieldSnapshot(MacroYieldSnapshot snapshot) {
        return macroYieldRepository.findByRecordDate(snapshot.getRecordDate())
                .flatMap(existing -> {
                    existing.setUsCorporateBondEffectiveYield(snapshot.getUsCorporateBondEffectiveYield());
                    existing.setUs10YearTreasuryYield(snapshot.getUs10YearTreasuryYield());
                    existing.setUs20YearTreasuryYield(snapshot.getUs20YearTreasuryYield());
                    existing.setYieldSpread10yMinus2y(snapshot.getYieldSpread10yMinus2y());
                    return macroYieldRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> macroYieldRepository.save(snapshot)));
    }

    private Mono<Integer> syncDcaRanks(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.DCA_RANKS) {
            return Mono.just(0);
        }

        if (scope == SyncScope.ALL && watermarkRepository != null) {
            return watermarkRepository.findByFeedName(WATERMARK_TWSE_DCA_RANKINGS)
                    .flatMap(wm -> {
                        if ("SUCCESS".equalsIgnoreCase(wm.getStatus()) && wm.getLatestRecordDate() != null
                                && wm.getRecordsSyncedCount() != null && wm.getRecordsSyncedCount() > 0) {
                            java.time.YearMonth reportMonth = (now.getDayOfMonth() >= 11)
                                    ? java.time.YearMonth.from(now).minusMonths(1)
                                    : java.time.YearMonth.from(now).minusMonths(2);
                            if (wm.getLatestRecordDate().getYear() == reportMonth.getYear()
                                    && wm.getLatestRecordDate().getMonthValue() == reportMonth.getMonthValue()) {
                                log.info("TWSE DCA rankings for {}-{} have already been synced (Watermark SUCCESS, count={}). Skipping monthly sync.",
                                        reportMonth.getYear(), reportMonth.getMonthValue(), wm.getRecordsSyncedCount());
                                return Mono.just(0);
                            }
                        }
                        return executeSyncDcaRanks(now);
                    })
                    .switchIfEmpty(Mono.defer(() -> executeSyncDcaRanks(now)));
        }

        return executeSyncDcaRanks(now);
    }

    private Mono<Integer> executeSyncDcaRanks(LocalDateTime now) {
        log.info("Fetching and syncing regular quota (DCA) Top 20 rankings from TWSE...");
        java.time.YearMonth reportMonth = (now.getDayOfMonth() >= 11)
                ? java.time.YearMonth.from(now).minusMonths(1)
                : java.time.YearMonth.from(now).minusMonths(2);
        int reportYear = reportMonth.getYear();
        int reportMonthVal = reportMonth.getMonthValue();
        log.info("Target TWSE DCA ranking report month: {}-{}", reportYear, reportMonthVal);

        return externalMarketDataPort.fetchDcaPopularityRanks(reportYear, reportMonthVal)
                .flatMap(rank -> metadataRepository.findByTicker(rank.getTicker())
                        .flatMap(asset -> {
                            rank.setAssetId(asset.getId());
                            return saveOrUpdateDcaRank(rank);
                        })
                        .switchIfEmpty(Mono.defer(() -> saveOrUpdateDcaRank(rank))))
                .count()
                .map(Long::intValue)
                .flatMap(dcaCount -> {
                    if (dcaCount > 0) {
                        LocalDateTime recordDate = reportMonth.atEndOfMonth().atStartOfDay();
                        return updateWatermarkSuccess(WATERMARK_TWSE_DCA_RANKINGS, now, recordDate, dcaCount)
                                .thenReturn(dcaCount);
                    } else {
                        return updateWatermarkFailed(WATERMARK_TWSE_DCA_RANKINGS, now, "No DCA rankings fetched for " + reportYear + "-" + reportMonthVal)
                                .thenReturn(0);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync DCA rankings: {}", e.getMessage(), e);
                    return updateWatermarkFailed(WATERMARK_TWSE_DCA_RANKINGS, now, extractErrorMessage(e))
                            .thenReturn(0);
                });
    }

    private Mono<DcaPopularityRank> saveOrUpdateDcaRank(DcaPopularityRank rank) {
        return dcaRankRepository.findByTickerAndRankingYearAndRankingMonth(
                        rank.getTicker(), rank.getRankingYear(), rank.getRankingMonth())
                .flatMap(existing -> {
                    existing.setRankPosition(rank.getRankPosition());
                    existing.setRegularInvestorCount(rank.getRegularInvestorCount());
                    if (rank.getAssetId() != null) {
                        existing.setAssetId(rank.getAssetId());
                    }
                    return dcaRankRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> dcaRankRepository.save(rank)));
    }

    private Mono<int[]> syncDividendsAndSplits(SyncScope scope, LocalDateTime now, Integer backfillDays) {
        if (scope != SyncScope.ALL && scope != SyncScope.DIVIDENDS_AND_SPLITS) {
            return Mono.just(new int[]{0, 0});
        }

        boolean forceBackfill = backfillDays != null && backfillDays > 0;

        // Monthly check: if running as part of scope ALL without force backfill, skip if already synced in current calendar month
        if (scope == SyncScope.ALL && !forceBackfill && watermarkRepository != null) {
            return watermarkRepository.findByFeedName(WATERMARK_DIVIDENDS_AND_SPLITS)
                    .flatMap(wm -> {
                        if ("SUCCESS".equalsIgnoreCase(wm.getStatus())
                                && wm.getRecordsSyncedCount() != null && wm.getRecordsSyncedCount() > 0
                                && wm.getLatestRecordDate() != null) {
                            if (wm.getLatestRecordDate().getYear() == now.getYear()
                                    && wm.getLatestRecordDate().getMonthValue() == now.getMonthValue()) {
                                log.info("Dividends and stock splits for {}-{} have already been synced (Watermark SUCCESS, count={}). Skipping monthly sync.",
                                        now.getYear(), now.getMonthValue(), wm.getRecordsSyncedCount());
                                return Mono.just(new int[]{0, 0});
                            }
                        }
                        return executeSyncDividendsAndSplits(now, backfillDays);
                    })
                    .switchIfEmpty(Mono.defer(() -> executeSyncDividendsAndSplits(now, backfillDays)));
        }

        return executeSyncDividendsAndSplits(now, backfillDays);
    }

    private Mono<int[]> executeSyncDividendsAndSplits(LocalDateTime now, Integer backfillDays) {
        log.info("Fetching and syncing dividend announcements and stock splits via concurrent Yahoo Finance calls...");
        return watermarkRepository.findByFeedName(WATERMARK_DIVIDENDS_AND_SPLITS)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(wmOpt -> {
                    LocalDateTime startDate;
                    String range;
                    if (backfillDays != null && backfillDays > 0) {
                        startDate = now.minusDays(backfillDays);
                        range = deriveRange(backfillDays);
                    } else if (wmOpt.isPresent() && wmOpt.get().getLatestRecordDate() != null) {
                        startDate = wmOpt.get().getLatestRecordDate();
                        int missedDays = (int) Math.max(ChronoUnit.DAYS.between(startDate.toLocalDate(), now.toLocalDate()), 30);
                        range = deriveRange(missedDays);
                    } else {
                        startDate = now.minusYears(1);
                        range = "1y";
                    }

                    log.info("Executing dividends and splits sync: startDate={}, range='{}'...", startDate, range);

                    return metadataRepository.findAll()
                            .flatMap(asset -> externalMarketDataPort.fetchDividendsAndSplits(asset.getTicker(), range)
                                    .flatMap(res -> {
                                        Mono<Integer> divCount = Flux.fromIterable(res.dividends())
                                                .filter(div -> div.getExDate() != null && !div.getExDate().isBefore(startDate))
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

                                        Mono<Integer> splitCount = Flux.fromIterable(res.splits())
                                                .filter(split -> split.getEffectiveDate() != null && !split.getEffectiveDate().isBefore(startDate))
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
                                    }), 8
                            )
                            .reduce(new int[]{0, 0}, (acc, cur) -> new int[]{acc[0] + cur[0], acc[1] + cur[1]})
                            .flatMap(counts -> {
                                int total = counts[0] + counts[1];
                                if (total > 0) {
                                    return updateWatermarkSuccess(WATERMARK_DIVIDENDS_AND_SPLITS, now, now, total)
                                            .thenReturn(counts);
                                } else {
                                    return updateWatermarkFailed(WATERMARK_DIVIDENDS_AND_SPLITS, now, "No dividend or split records synced")
                                            .thenReturn(counts);
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync dividends and splits: {}", e.getMessage(), e);
                    return updateWatermarkFailed(WATERMARK_DIVIDENDS_AND_SPLITS, now, extractErrorMessage(e))
                            .thenReturn(new int[]{0, 0});
                });
    }
}
