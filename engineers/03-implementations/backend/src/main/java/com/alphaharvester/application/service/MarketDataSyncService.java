package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.dto.SyncedRecordsCount;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.domain.model.SyncScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class MarketDataSyncService implements MarketDataSyncUseCase {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncService.class);

    private final ExternalMarketDataPort externalMarketDataPort;
    private final GlobalAssetMetadataRepository metadataRepository;
    private final BenchmarkIndexRepository benchmarkRepository;
    private final MarketDailyQuoteRepository quoteRepository;
    private final MacroYieldSnapshotRepository macroYieldRepository;
    private final DcaPopularityRankRepository dcaRankRepository;
    private final DividendAnnouncementRepository dividendRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final GlobalAssetScoreEvaluationService scoreEvaluationService;

    public MarketDataSyncService(ExternalMarketDataPort externalMarketDataPort,
                                 GlobalAssetMetadataRepository metadataRepository,
                                 BenchmarkIndexRepository benchmarkRepository,
                                 MarketDailyQuoteRepository quoteRepository,
                                 MacroYieldSnapshotRepository macroYieldRepository,
                                 DcaPopularityRankRepository dcaRankRepository,
                                 DividendAnnouncementRepository dividendRepository,
                                 CorporateActionRepository corporateActionRepository,
                                 GlobalAssetScoreEvaluationService scoreEvaluationService) {
        this.externalMarketDataPort = externalMarketDataPort;
        this.metadataRepository = metadataRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.quoteRepository = quoteRepository;
        this.macroYieldRepository = macroYieldRepository;
        this.dcaRankRepository = dcaRankRepository;
        this.dividendRepository = dividendRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.scoreEvaluationService = scoreEvaluationService;
    }

    @Override
    @Transactional
    public Mono<MarketDataSyncResponse> syncMarketData(MarketDataSyncRequest request) {
        SyncScope scope = request.scope() != null ? request.scope() : SyncScope.ALL;
        LocalDateTime now = LocalDateTime.now();
        log.info("Initiating market data synchronization pipeline. Scope: {}, ExecutedAt: {}", scope, now);

        return executeSync(scope, now)
                .flatMap(counts -> {
                    if (Boolean.TRUE.equals(request.evaluateAfterSync())) {
                        log.info("Triggering post-sync candidate multi-factor evaluation...");
                        return scoreEvaluationService.evaluateGlobalAssetScores()
                                .thenReturn(counts);
                    }
                    return Mono.just(counts);
                })
                .map(counts -> new MarketDataSyncResponse(
                        "SUCCESS",
                        "Market data synchronization completed successfully.",
                        now.toString(),
                        counts
                ))
                .doOnError(e -> log.error("Market data synchronization pipeline failed: {}", e.getMessage(), e));
    }

    private Mono<SyncedRecordsCount> executeSync(SyncScope scope, LocalDateTime now) {
        return syncMetadata(scope, now)
                .flatMap(metaCount -> syncQuotes(scope, now)
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
        log.info("Fetching and syncing ETF metadata catalog from external data feed...");
        return externalMarketDataPort.fetchEtfMasterUniverse()
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
                .map(Long::intValue);
    }

    private Mono<Integer> syncQuotes(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.QUOTES) {
            return Mono.just(0);
        }
        log.info("Fetching and syncing daily market quotes from TWSE, TPEx, and Yahoo Finance...");
        return externalMarketDataPort.fetchDailyQuotes()
                .flatMap(q -> quoteRepository.findByTickerAndTradeDate(q.getTicker(), q.getTradeDate())
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
                        .switchIfEmpty(quoteRepository.save(q)))
                .count()
                .map(Long::intValue);
    }

    private Mono<Integer> syncMacroYields(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.MACRO_YIELDS) {
            return Mono.just(0);
        }
        log.info("Fetching and syncing macroeconomic treasury yields from Yahoo Finance...");
        return externalMarketDataPort.fetchLatestMacroYield()
                .flatMap(snapshot -> macroYieldRepository.findByRecordDate(snapshot.getRecordDate())
                        .flatMap(existing -> {
                            existing.setUsCorporateBondEffectiveYield(snapshot.getUsCorporateBondEffectiveYield());
                            existing.setUs10YearTreasuryYield(snapshot.getUs10YearTreasuryYield());
                            existing.setUs20YearTreasuryYield(snapshot.getUs20YearTreasuryYield());
                            existing.setYieldSpread10yMinus2y(snapshot.getYieldSpread10yMinus2y());
                            return macroYieldRepository.save(existing);
                        })
                        .switchIfEmpty(macroYieldRepository.save(snapshot))
                        .thenReturn(1))
                .defaultIfEmpty(0);
    }

    private Mono<Integer> syncDcaRanks(SyncScope scope, LocalDateTime now) {
        if (scope != SyncScope.ALL && scope != SyncScope.DCA_RANKS) {
            return Mono.just(0);
        }
        log.info("Fetching and syncing regular quota (DCA) Top 20 rankings from TWSE...");
        return externalMarketDataPort.fetchDcaPopularityRanks(now.getYear(), now.getMonthValue())
                .flatMap(rank -> metadataRepository.findByTicker(rank.getTicker())
                        .flatMap(asset -> {
                            rank.setAssetId(asset.getId());
                            return dcaRankRepository.save(rank);
                        })
                        .switchIfEmpty(dcaRankRepository.save(rank)))
                .count()
                .map(Long::intValue);
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
                                return dividendRepository.save(div);
                            })
                            .count()
                            .map(Long::intValue);

                    Mono<Integer> splitCount = externalMarketDataPort.fetchCorporateActions(asset.getTicker())
                            .flatMap(split -> {
                                split.setAssetId(asset.getId());
                                return corporateActionRepository.save(split);
                            })
                            .count()
                            .map(Long::intValue);

                    return Mono.zip(divCount, splitCount, (d, s) -> new int[]{d, s});
                })
                .reduce(new int[]{0, 0}, (acc, cur) -> new int[]{acc[0] + cur[0], acc[1] + cur[1]});
    }
}
