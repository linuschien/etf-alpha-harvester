package com.alphaharvester.adapter.in.web.graphql;

import com.alphaharvester.application.dto.*;
import com.alphaharvester.application.service.DipBuyOpportunityService;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.application.service.MacroYieldEvaluationService;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.CandidateAssetClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Controller
public class GlobalServicesGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(GlobalServicesGraphQLResolver.class);

    private final GlobalAssetQueryService queryService;
    private final DipBuyOpportunityService dipBuyService;
    private final MacroYieldEvaluationService macroYieldService;

    public GlobalServicesGraphQLResolver(GlobalAssetQueryService queryService,
                                         DipBuyOpportunityService dipBuyService,
                                         MacroYieldEvaluationService macroYieldService) {
        this.queryService = queryService;
        this.dipBuyService = dipBuyService;
        this.macroYieldService = macroYieldService;
    }

    // ----------------------------------------------------
    // GlobalAssetMetadata Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<GlobalAssetMetadata> listGlobalAssets(@Argument GlobalAssetFilterInput filter) {
        log.debug("GraphQL Query: listGlobalAssets with filter: {}", filter);
        return queryService.listGlobalAssets(filter);
    }

    @QueryMapping
    public Mono<GlobalAssetMetadata> getGlobalAssetById(@Argument UUID id) {
        log.debug("GraphQL Query: getGlobalAssetById({})", id);
        return queryService.getGlobalAssetById(id);
    }

    @QueryMapping
    public Mono<GlobalAssetMetadata> getGlobalAssetByTicker(@Argument String ticker) {
        log.debug("GraphQL Query: getGlobalAssetByTicker({})", ticker);
        return queryService.getGlobalAssetByTicker(ticker);
    }

    // ----------------------------------------------------
    // BenchmarkIndex Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<BenchmarkIndex> listBenchmarkIndices() {
        log.debug("GraphQL Query: listBenchmarkIndices");
        return queryService.listBenchmarkIndices();
    }

    @QueryMapping
    public Mono<BenchmarkIndex> getBenchmarkIndexById(@Argument UUID id) {
        log.debug("GraphQL Query: getBenchmarkIndexById({})", id);
        return queryService.getBenchmarkIndexById(id);
    }

    @QueryMapping
    public Mono<BenchmarkIndex> getBenchmarkIndexByTicker(@Argument String ticker) {
        log.debug("GraphQL Query: getBenchmarkIndexByTicker({})", ticker);
        return queryService.getBenchmarkIndexByTicker(ticker);
    }

    // ----------------------------------------------------
    // MarketDailyQuote Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<MarketDailyQuote> listMarketDailyQuotes(@Argument MarketDailyQuoteFilterInput filter) {
        log.debug("GraphQL Query: listMarketDailyQuotes with filter: {}", filter);
        return queryService.listMarketDailyQuotes(filter);
    }

    @QueryMapping
    public Mono<MarketDailyQuote> getMarketDailyQuoteById(@Argument UUID id) {
        log.debug("GraphQL Query: getMarketDailyQuoteById({})", id);
        return queryService.getMarketDailyQuoteById(id);
    }

    @QueryMapping
    public Flux<MarketDailyQuote> getQuoteTimeSeries(@Argument String ticker,
                                                     @Argument String startDate,
                                                     @Argument String endDate) {
        log.debug("GraphQL Query: getQuoteTimeSeries({}, {}, {})", ticker, startDate, endDate);
        return queryService.getQuoteTimeSeries(ticker, startDate, endDate);
    }

    @QueryMapping
    public Flux<MarketDailyQuote> listQuotesByAssetId(@Argument UUID assetId) {
        log.debug("GraphQL Query: listQuotesByAssetId({})", assetId);
        return queryService.listQuotesByAssetId(assetId);
    }

    @QueryMapping
    public Flux<MarketDailyQuote> listQuotesByBenchmarkId(@Argument UUID benchmarkId) {
        log.debug("GraphQL Query: listQuotesByBenchmarkId({})", benchmarkId);
        return queryService.listQuotesByBenchmarkId(benchmarkId);
    }

    // ----------------------------------------------------
    // MacroYieldSnapshot Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<MacroYieldSnapshot> listMacroYieldSnapshots(@Argument MacroYieldFilterInput filter) {
        log.debug("GraphQL Query: listMacroYieldSnapshots with filter: {}", filter);
        return queryService.listMacroYieldSnapshots(filter);
    }

    @QueryMapping
    public Mono<MacroYieldSnapshot> getMacroYieldSnapshotById(@Argument UUID id) {
        log.debug("GraphQL Query: getMacroYieldSnapshotById({})", id);
        return queryService.getMacroYieldSnapshotById(id);
    }

    @QueryMapping
    public Mono<MacroYieldSnapshot> getLatestMacroYieldSnapshot() {
        log.debug("GraphQL Query: getLatestMacroYieldSnapshot");
        return queryService.getLatestMacroYieldSnapshot();
    }

    // ----------------------------------------------------
    // GlobalAssetScore Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<GlobalAssetScore> listGlobalAssetScores(@Argument GlobalAssetScoreFilterInput filter) {
        log.debug("GraphQL Query: listGlobalAssetScores with filter: {}", filter);
        return queryService.listGlobalAssetScores(filter);
    }

    @QueryMapping
    public Mono<GlobalAssetScore> getGlobalAssetScoreById(@Argument UUID id) {
        log.debug("GraphQL Query: getGlobalAssetScoreById({})", id);
        return queryService.getGlobalAssetScoreById(id);
    }

    @QueryMapping
    public Flux<GlobalAssetScore> getScoresByAssetClass(@Argument CandidateAssetClass assetClass,
                                                        @Argument String evaluationDate) {
        log.debug("GraphQL Query: getScoresByAssetClass({}, {})", assetClass, evaluationDate);
        return queryService.listGlobalAssetScores(new GlobalAssetScoreFilterInput(assetClass, evaluationDate, true));
    }

    @QueryMapping
    public Mono<GlobalAssetScore> getScoreByTicker(@Argument String ticker,
                                                   @Argument String evaluationDate) {
        log.debug("GraphQL Query: getScoreByTicker({}, {})", ticker, evaluationDate);
        return queryService.listGlobalAssetScores(null)
                .filter(s -> s.getTicker().equals(ticker))
                .next();
    }

    // ----------------------------------------------------
    // DcaPopularityRank Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<DcaPopularityRank> listDcaPopularityRanks(@Argument DcaPopularityFilterInput filter) {
        log.debug("GraphQL Query: listDcaPopularityRanks with filter: {}", filter);
        return queryService.listDcaPopularityRanks(filter);
    }

    @QueryMapping
    public Mono<DcaPopularityRank> getDcaPopularityRankById(@Argument UUID id) {
        log.debug("GraphQL Query: getDcaPopularityRankById({})", id);
        return queryService.getDcaPopularityRankById(id);
    }

    @QueryMapping
    public Flux<DcaPopularityRank> getTop20DcaRanks(@Argument Integer year, @Argument Integer month) {
        log.debug("GraphQL Query: getTop20DcaRanks({}, {})", year, month);
        return queryService.getTop20DcaRanks(year, month);
    }

    @QueryMapping
    public Flux<DcaPopularityRank> listDcaRanksByAssetId(@Argument UUID assetId) {
        log.debug("GraphQL Query: listDcaRanksByAssetId({})", assetId);
        return queryService.listDcaRanksByAssetId(assetId);
    }

    // ----------------------------------------------------
    // DividendAnnouncement Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<DividendAnnouncement> listDividendAnnouncements(@Argument DividendAnnouncementFilterInput filter) {
        log.debug("GraphQL Query: listDividendAnnouncements with filter: {}", filter);
        return queryService.listDividendAnnouncements(filter);
    }

    @QueryMapping
    public Mono<DividendAnnouncement> getDividendAnnouncementById(@Argument UUID id) {
        log.debug("GraphQL Query: getDividendAnnouncementById({})", id);
        return queryService.getDividendAnnouncementById(id);
    }

    @QueryMapping
    public Flux<DividendAnnouncement> getUpcomingDividends(@Argument String startDate, @Argument String endDate) {
        log.debug("GraphQL Query: getUpcomingDividends({}, {})", startDate, endDate);
        return queryService.getUpcomingDividends(startDate, endDate);
    }

    @QueryMapping
    public Flux<DividendAnnouncement> listDividendsByAssetId(@Argument UUID assetId) {
        log.debug("GraphQL Query: listDividendsByAssetId({})", assetId);
        return queryService.listDividendsByAssetId(assetId);
    }

    // ----------------------------------------------------
    // CorporateAction Queries
    // ----------------------------------------------------

    @QueryMapping
    public Flux<CorporateAction> listCorporateActions(@Argument CorporateActionFilterInput filter) {
        log.debug("GraphQL Query: listCorporateActions with filter: {}", filter);
        return queryService.listCorporateActions(filter);
    }

    @QueryMapping
    public Mono<CorporateAction> getCorporateActionById(@Argument UUID id) {
        log.debug("GraphQL Query: getCorporateActionById({})", id);
        return queryService.getCorporateActionById(id);
    }

    @QueryMapping
    public Flux<CorporateAction> getEffectiveSplits(@Argument String ticker, @Argument String effectiveDate) {
        log.debug("GraphQL Query: getEffectiveSplits({}, {})", ticker, effectiveDate);
        return queryService.getEffectiveSplits(ticker, effectiveDate);
    }

    @QueryMapping
    public Flux<CorporateAction> listCorporateActionsByAssetId(@Argument UUID assetId) {
        log.debug("GraphQL Query: listCorporateActionsByAssetId({})", assetId);
        return queryService.listCorporateActionsByAssetId(assetId);
    }

    // ----------------------------------------------------
    // Pure Quantitative Function Queries
    // ----------------------------------------------------

    @QueryMapping
    public Mono<DipBuyOpportunityScore> getDipBuyOpportunity(@Argument String ticker) {
        log.debug("GraphQL Query: getDipBuyOpportunity({})", ticker);
        return dipBuyService.calculateDipBuyOpportunity(ticker);
    }

    @QueryMapping
    public Mono<MacroRegimeAssessment> getMacroRegime() {
        log.debug("GraphQL Query: getMacroRegime");
        return macroYieldService.evaluateCurrentRegime();
    }

    // ----------------------------------------------------
    // Schema Mappings for Type Conversions
    // ----------------------------------------------------

    @SchemaMapping(typeName = "GlobalAssetMetadata", field = "id")
    public String globalAssetMetadataId(GlobalAssetMetadata asset) {
        return asset.getId() != null ? asset.getId().toString() : null;
    }

    @SchemaMapping(typeName = "GlobalAssetMetadata", field = "listingDate")
    public String globalAssetMetadataListingDate(GlobalAssetMetadata asset) {
        return asset.getListingDate() != null ? asset.getListingDate().toString() : null;
    }

    @SchemaMapping(typeName = "GlobalAssetMetadata", field = "createdAt")
    public String globalAssetMetadataCreatedAt(GlobalAssetMetadata asset) {
        return asset.getCreatedAt() != null ? asset.getCreatedAt().toString() : null;
    }

    @SchemaMapping(typeName = "GlobalAssetMetadata", field = "updatedAt")
    public String globalAssetMetadataUpdatedAt(GlobalAssetMetadata asset) {
        return asset.getUpdatedAt() != null ? asset.getUpdatedAt().toString() : null;
    }

    @SchemaMapping(typeName = "BenchmarkIndex", field = "id")
    public String benchmarkIndexId(BenchmarkIndex benchmark) {
        return benchmark.getId() != null ? benchmark.getId().toString() : null;
    }

    @SchemaMapping(typeName = "BenchmarkIndex", field = "createdAt")
    public String benchmarkIndexCreatedAt(BenchmarkIndex benchmark) {
        return benchmark.getCreatedAt() != null ? benchmark.getCreatedAt().toString() : null;
    }

    @SchemaMapping(typeName = "BenchmarkIndex", field = "updatedAt")
    public String benchmarkIndexUpdatedAt(BenchmarkIndex benchmark) {
        return benchmark.getUpdatedAt() != null ? benchmark.getUpdatedAt().toString() : null;
    }

    @SchemaMapping(typeName = "MarketDailyQuote", field = "id")
    public String marketDailyQuoteId(MarketDailyQuote quote) {
        return quote.getId() != null ? quote.getId().toString() : null;
    }

    @SchemaMapping(typeName = "MarketDailyQuote", field = "assetId")
    public String marketDailyQuoteAssetId(MarketDailyQuote quote) {
        return quote.getAssetId() != null ? quote.getAssetId().toString() : null;
    }

    @SchemaMapping(typeName = "MarketDailyQuote", field = "benchmarkId")
    public String marketDailyQuoteBenchmarkId(MarketDailyQuote quote) {
        return quote.getBenchmarkId() != null ? quote.getBenchmarkId().toString() : null;
    }

    @SchemaMapping(typeName = "MarketDailyQuote", field = "tradeDate")
    public String marketDailyQuoteTradeDate(MarketDailyQuote quote) {
        return quote.getTradeDate() != null ? quote.getTradeDate().toString() : null;
    }

    @SchemaMapping(typeName = "MacroYieldSnapshot", field = "id")
    public String macroYieldSnapshotId(MacroYieldSnapshot snapshot) {
        return snapshot.getId() != null ? snapshot.getId().toString() : null;
    }

    @SchemaMapping(typeName = "MacroYieldSnapshot", field = "recordDate")
    public String macroYieldSnapshotRecordDate(MacroYieldSnapshot snapshot) {
        return snapshot.getRecordDate() != null ? snapshot.getRecordDate().toString() : null;
    }

    @SchemaMapping(typeName = "GlobalAssetScore", field = "id")
    public String globalAssetScoreId(GlobalAssetScore score) {
        return score.getId() != null ? score.getId().toString() : null;
    }

    @SchemaMapping(typeName = "GlobalAssetScore", field = "assetId")
    public String globalAssetScoreAssetId(GlobalAssetScore score) {
        return score.getAssetId() != null ? score.getAssetId().toString() : null;
    }

    @SchemaMapping(typeName = "GlobalAssetScore", field = "evaluationDate")
    public String globalAssetScoreEvaluationDate(GlobalAssetScore score) {
        return score.getEvaluationDate() != null ? score.getEvaluationDate().toString() : null;
    }

    @SchemaMapping(typeName = "DcaPopularityRank", field = "id")
    public String dcaPopularityRankId(DcaPopularityRank rank) {
        return rank.getId() != null ? rank.getId().toString() : null;
    }

    @SchemaMapping(typeName = "DcaPopularityRank", field = "assetId")
    public String dcaPopularityRankAssetId(DcaPopularityRank rank) {
        return rank.getAssetId() != null ? rank.getAssetId().toString() : null;
    }

    @SchemaMapping(typeName = "DividendAnnouncement", field = "id")
    public String dividendAnnouncementId(DividendAnnouncement div) {
        return div.getId() != null ? div.getId().toString() : null;
    }

    @SchemaMapping(typeName = "DividendAnnouncement", field = "assetId")
    public String dividendAnnouncementAssetId(DividendAnnouncement div) {
        return div.getAssetId() != null ? div.getAssetId().toString() : null;
    }

    @SchemaMapping(typeName = "DividendAnnouncement", field = "exDate")
    public String dividendAnnouncementExDate(DividendAnnouncement div) {
        return div.getExDate() != null ? div.getExDate().toString() : null;
    }

    @SchemaMapping(typeName = "DividendAnnouncement", field = "paymentDate")
    public String dividendAnnouncementPaymentDate(DividendAnnouncement div) {
        return div.getPaymentDate() != null ? div.getPaymentDate().toString() : null;
    }

    @SchemaMapping(typeName = "CorporateAction", field = "id")
    public String corporateActionId(CorporateAction ca) {
        return ca.getId() != null ? ca.getId().toString() : null;
    }

    @SchemaMapping(typeName = "CorporateAction", field = "assetId")
    public String corporateActionAssetId(CorporateAction ca) {
        return ca.getAssetId() != null ? ca.getAssetId().toString() : null;
    }

    @SchemaMapping(typeName = "CorporateAction", field = "effectiveDate")
    public String corporateActionEffectiveDate(CorporateAction ca) {
        return ca.getEffectiveDate() != null ? ca.getEffectiveDate().toString() : null;
    }
}

