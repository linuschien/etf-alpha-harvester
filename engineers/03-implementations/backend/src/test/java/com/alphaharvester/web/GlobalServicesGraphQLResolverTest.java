package com.alphaharvester.web;

import com.alphaharvester.adapter.in.web.graphql.GlobalServicesGraphQLResolver;
import com.alphaharvester.application.dto.*;
import com.alphaharvester.application.service.DipBuyOpportunityService;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.application.service.MacroYieldEvaluationService;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalServicesGraphQLResolverTest {

    @Mock private GlobalAssetQueryService queryService;
    @Mock private DipBuyOpportunityService dipBuyService;
    @Mock private MacroYieldEvaluationService macroYieldService;

    private GlobalServicesGraphQLResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GlobalServicesGraphQLResolver(queryService, dipBuyService, macroYieldService);
    }

    @Test
    @DisplayName("Should test all GraphQL query mappings")
    void shouldTestQueryMappings() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", now, null, null,
                null, null, CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null);
        BenchmarkIndex benchmark = new BenchmarkIndex(id, "^TWII", "加權指數", "TW", "台股大盤", 1, now, now, null);
        MarketDailyQuote quote = new MarketDailyQuote(id, id, null, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 1000000L, new BigDecimal("188000000"), null, null);
        MacroYieldSnapshot snap = new MacroYieldSnapshot(id, now, new BigDecimal("5.25"),
                new BigDecimal("4.20"), new BigDecimal("4.50"), new BigDecimal("-0.10"));
        GlobalAssetScore score = new GlobalAssetScore(id, id, "0050", now, CandidateAssetClass.CORE, 1,
                new BigDecimal("94.5"), new BigDecimal("0.0043"), new BigDecimal("420000000000"), true, null);
        DcaPopularityRank dca = new DcaPopularityRank(id, id, "0050", 2026, 8, 1, 1280000);
        DividendAnnouncement div = new DividendAnnouncement(id, id, "0050", now, now.plusDays(30), new BigDecimal("1.5"), TaxTag.DOMESTIC_54C);
        CorporateAction ca = new CorporateAction(id, id, "0050", CorporateActionType.SPLIT, now, 4, 1);

        when(queryService.listGlobalAssets(any())).thenReturn(Flux.just(asset));
        when(queryService.getGlobalAssetById(id)).thenReturn(Mono.just(asset));
        when(queryService.getGlobalAssetByTicker("0050")).thenReturn(Mono.just(asset));

        when(queryService.listBenchmarkIndices()).thenReturn(Flux.just(benchmark));
        when(queryService.getBenchmarkIndexById(id)).thenReturn(Mono.just(benchmark));
        when(queryService.getBenchmarkIndexByTicker("^TWII")).thenReturn(Mono.just(benchmark));

        when(queryService.listMarketDailyQuotes(any())).thenReturn(Flux.just(quote));
        when(queryService.getMarketDailyQuoteById(id)).thenReturn(Mono.just(quote));
        when(queryService.getQuoteTimeSeries(any(), any(), any())).thenReturn(Flux.just(quote));
        when(queryService.listQuotesByAssetId(id)).thenReturn(Flux.just(quote));
        when(queryService.listQuotesByBenchmarkId(id)).thenReturn(Flux.just(quote));

        when(queryService.listMacroYieldSnapshots(any())).thenReturn(Flux.just(snap));
        when(queryService.getMacroYieldSnapshotById(id)).thenReturn(Mono.just(snap));
        when(queryService.getLatestMacroYieldSnapshot()).thenReturn(Mono.just(snap));

        when(queryService.listGlobalAssetScores(any())).thenReturn(Flux.just(score));
        when(queryService.getGlobalAssetScoreById(id)).thenReturn(Mono.just(score));

        when(queryService.listDcaPopularityRanks(any())).thenReturn(Flux.just(dca));
        when(queryService.getDcaPopularityRankById(id)).thenReturn(Mono.just(dca));
        when(queryService.getTop20DcaRanks(2026, 8)).thenReturn(Flux.just(dca));
        when(queryService.listDcaRanksByAssetId(id)).thenReturn(Flux.just(dca));

        when(queryService.listDividendAnnouncements(any())).thenReturn(Flux.just(div));
        when(queryService.getDividendAnnouncementById(id)).thenReturn(Mono.just(div));
        when(queryService.getUpcomingDividends(any(), any())).thenReturn(Flux.just(div));
        when(queryService.listDividendsByAssetId(id)).thenReturn(Flux.just(div));

        when(queryService.listCorporateActions(any())).thenReturn(Flux.just(ca));
        when(queryService.getCorporateActionById(id)).thenReturn(Mono.just(ca));
        when(queryService.getEffectiveSplits(any(), any())).thenReturn(Flux.just(ca));
        when(queryService.listCorporateActionsByAssetId(id)).thenReturn(Flux.just(ca));

        when(dipBuyService.calculateDipBuyOpportunity("0050")).thenReturn(Mono.just(
                new DipBuyOpportunityScore("0050", 85.0, "🟢 【五星黃金坑】", "≥ 90%", "加碼", 30, 20, 20, 15)
        ));
        when(macroYieldService.evaluateCurrentRegime()).thenReturn(Mono.just(
                new MacroRegimeAssessment(MacroState.HIGH_YIELD_ACCUMULATION, 0.8, 0.2, 5.25, "OK", CrisisLevel.NORMAL)
        ));

        // Verify resolver queries
        StepVerifier.create(resolver.listGlobalAssets(null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getGlobalAssetById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getGlobalAssetByTicker("0050")).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.listBenchmarkIndices()).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getBenchmarkIndexById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getBenchmarkIndexByTicker("^TWII")).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.listMarketDailyQuotes(null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getMarketDailyQuoteById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getQuoteTimeSeries("0050", "2026-09-01", "2026-09-23")).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.listQuotesByAssetId(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.listQuotesByBenchmarkId(id)).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.listMacroYieldSnapshots(null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getMacroYieldSnapshotById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getLatestMacroYieldSnapshot()).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.listGlobalAssetScores(null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getGlobalAssetScoreById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getScoresByAssetClass(CandidateAssetClass.CORE, "2026-09-23")).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getScoreByTicker("0050", "2026-09-23")).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.listDcaPopularityRanks(null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getDcaPopularityRankById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getTop20DcaRanks(2026, 8)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.listDcaRanksByAssetId(id)).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.listDividendAnnouncements(null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getDividendAnnouncementById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getUpcomingDividends("2026-09-01", "2026-09-30")).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.listDividendsByAssetId(id)).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.listCorporateActions(null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getCorporateActionById(id)).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getEffectiveSplits("0050", "2026-09-23")).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.listCorporateActionsByAssetId(id)).expectNextCount(1).verifyComplete();

        StepVerifier.create(resolver.getDipBuyOpportunity("0050")).expectNextCount(1).verifyComplete();
        StepVerifier.create(resolver.getMacroRegime()).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("Should test all SchemaMapping methods for proper string serialization")
    void shouldTestSchemaMappings() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", now, null, null,
                null, null, CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null);
        BenchmarkIndex benchmark = new BenchmarkIndex(id, "^TWII", "加權指數", "TW", "台股大盤", 1, now, now, null);
        MarketDailyQuote quote = new MarketDailyQuote(id, id, id, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 1000000L, new BigDecimal("188000000"), null, null);
        MacroYieldSnapshot snap = new MacroYieldSnapshot(id, now, new BigDecimal("5.25"),
                new BigDecimal("4.20"), new BigDecimal("4.50"), new BigDecimal("-0.10"));
        GlobalAssetScore score = new GlobalAssetScore(id, id, "0050", now, CandidateAssetClass.CORE, 1,
                new BigDecimal("94.5"), new BigDecimal("0.0043"), new BigDecimal("420000000000"), true, null);
        DcaPopularityRank dca = new DcaPopularityRank(id, id, "0050", 2026, 8, 1, 1280000);
        DividendAnnouncement div = new DividendAnnouncement(id, id, "0050", now, now.plusDays(30), new BigDecimal("1.5"), TaxTag.DOMESTIC_54C);
        CorporateAction ca = new CorporateAction(id, id, "0050", CorporateActionType.SPLIT, now, 4, 1);

        assertThat(resolver.globalAssetMetadataId(asset)).isEqualTo(id.toString());
        assertThat(resolver.globalAssetMetadataListingDate(asset)).isEqualTo(now.toString());
        assertThat(resolver.globalAssetMetadataCreatedAt(asset)).isEqualTo(now.toString());
        assertThat(resolver.globalAssetMetadataUpdatedAt(asset)).isEqualTo(now.toString());

        assertThat(resolver.benchmarkIndexId(benchmark)).isEqualTo(id.toString());
        assertThat(resolver.benchmarkIndexCreatedAt(benchmark)).isEqualTo(now.toString());
        assertThat(resolver.benchmarkIndexUpdatedAt(benchmark)).isEqualTo(now.toString());

        assertThat(resolver.marketDailyQuoteId(quote)).isEqualTo(id.toString());
        assertThat(resolver.marketDailyQuoteAssetId(quote)).isEqualTo(id.toString());
        assertThat(resolver.marketDailyQuoteBenchmarkId(quote)).isEqualTo(id.toString());
        assertThat(resolver.marketDailyQuoteTradeDate(quote)).isEqualTo(now.toString());

        assertThat(resolver.macroYieldSnapshotId(snap)).isEqualTo(id.toString());
        assertThat(resolver.macroYieldSnapshotRecordDate(snap)).isEqualTo(now.toString());

        assertThat(resolver.globalAssetScoreId(score)).isEqualTo(id.toString());
        assertThat(resolver.globalAssetScoreAssetId(score)).isEqualTo(id.toString());
        assertThat(resolver.globalAssetScoreEvaluationDate(score)).isEqualTo(now.toString());

        assertThat(resolver.dcaPopularityRankId(dca)).isEqualTo(id.toString());
        assertThat(resolver.dcaPopularityRankAssetId(dca)).isEqualTo(id.toString());

        assertThat(resolver.dividendAnnouncementId(div)).isEqualTo(id.toString());
        assertThat(resolver.dividendAnnouncementAssetId(div)).isEqualTo(id.toString());
        assertThat(resolver.dividendAnnouncementExDate(div)).isEqualTo(now.toString());
        assertThat(resolver.dividendAnnouncementPaymentDate(div)).isEqualTo(now.plusDays(30).toString());

        assertThat(resolver.corporateActionId(ca)).isEqualTo(id.toString());
        assertThat(resolver.corporateActionAssetId(ca)).isEqualTo(id.toString());
        assertThat(resolver.corporateActionEffectiveDate(ca)).isEqualTo(now.toString());
    }
}

