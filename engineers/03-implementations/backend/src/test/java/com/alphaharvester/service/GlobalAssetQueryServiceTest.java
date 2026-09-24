package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.*;
import com.alphaharvester.application.dto.*;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.domain.entity.*;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.CorporateActionType;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.alphaharvester.domain.model.TaxTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Example;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalAssetQueryServiceTest {

    @Mock private GlobalAssetMetadataRepository metadataRepository;
    @Mock private BenchmarkIndexRepository benchmarkRepository;
    @Mock private MarketDailyQuoteRepository quoteRepository;
    @Mock private MacroYieldSnapshotRepository macroYieldRepository;
    @Mock private GlobalAssetScoreRepository scoreRepository;
    @Mock private DcaPopularityRankRepository dcaRankRepository;
    @Mock private DividendAnnouncementRepository dividendRepository;
    @Mock private CorporateActionRepository corporateActionRepository;

    private GlobalAssetQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new GlobalAssetQueryService(
                metadataRepository, benchmarkRepository, quoteRepository,
                macroYieldRepository, scoreRepository, dcaRankRepository,
                dividendRepository, corporateActionRepository
        );
    }

    @Test
    @DisplayName("Should query GlobalAssetMetadata with and without filter")
    void shouldQueryGlobalAssetMetadata() {
        UUID id = UUID.randomUUID();
        GlobalAssetMetadata asset = new GlobalAssetMetadata(id, "0050", "元大台灣50", LocalDateTime.now(),
                "臺灣50", new BigDecimal("420000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, LocalDateTime.now(), LocalDateTime.now(), null);

        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(metadataRepository.findById(id)).thenReturn(Mono.just(asset));
        when(metadataRepository.findByTicker("0050")).thenReturn(Mono.just(asset));
        when(metadataRepository.findAll(any(Example.class))).thenReturn(Flux.just(asset));

        StepVerifier.create(queryService.listGlobalAssets(null))
                .assertNext(a -> assertThat(a.getTicker()).isEqualTo("0050"))
                .verifyComplete();

        StepVerifier.create(queryService.listGlobalAssets(new GlobalAssetFilterInput("0050", CandidateAssetClass.CORE, null)))
                .assertNext(a -> assertThat(a.getTicker()).isEqualTo("0050"))
                .verifyComplete();

        StepVerifier.create(queryService.getGlobalAssetById(id))
                .assertNext(a -> assertThat(a.getId()).isEqualTo(id))
                .verifyComplete();

        StepVerifier.create(queryService.getGlobalAssetByTicker("0050"))
                .assertNext(a -> assertThat(a.getName()).isEqualTo("元大台灣50"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query BenchmarkIndex correctly")
    void shouldQueryBenchmarkIndex() {
        UUID id = UUID.randomUUID();
        BenchmarkIndex benchmark = new BenchmarkIndex(id, "^TWII", "加權指數", "TW", "台股大盤", 1, LocalDateTime.now(), LocalDateTime.now(), null);

        when(benchmarkRepository.findAll()).thenReturn(Flux.just(benchmark));
        when(benchmarkRepository.findById(id)).thenReturn(Mono.just(benchmark));
        when(benchmarkRepository.findByTicker("^TWII")).thenReturn(Mono.just(benchmark));

        StepVerifier.create(queryService.listBenchmarkIndices())
                .assertNext(b -> assertThat(b.getTicker()).isEqualTo("^TWII"))
                .verifyComplete();

        StepVerifier.create(queryService.getBenchmarkIndexById(id))
                .assertNext(b -> assertThat(b.getId()).isEqualTo(id))
                .verifyComplete();

        StepVerifier.create(queryService.getBenchmarkIndexByTicker("^TWII"))
                .assertNext(b -> assertThat(b.getRegion()).isEqualTo("TW"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query MarketDailyQuote and time series")
    void shouldQueryMarketDailyQuote() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        MarketDailyQuote quote = new MarketDailyQuote(id, null, null, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 1000000L, new BigDecimal("188000000"),
                new BigDecimal("188.2"), new BigDecimal("-0.1"));

        when(quoteRepository.findAll()).thenReturn(Flux.just(quote));
        when(quoteRepository.findById(id)).thenReturn(Mono.just(quote));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.just(quote));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any())).thenReturn(Flux.just(quote));

        StepVerifier.create(queryService.listMarketDailyQuotes(null))
                .assertNext(q -> assertThat(q.getTicker()).isEqualTo("0050"))
                .verifyComplete();

        StepVerifier.create(queryService.getQuoteTimeSeries("0050", "2026-09-01", "2026-09-23"))
                .assertNext(q -> assertThat(q.getClosePrice()).isEqualTo(new BigDecimal("188.0")))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query MacroYieldSnapshot and latest snapshot")
    void shouldQueryMacroYieldSnapshot() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot snap = new MacroYieldSnapshot(id, now, new BigDecimal("5.25"),
                new BigDecimal("4.20"), new BigDecimal("4.50"), new BigDecimal("-0.10"));

        when(macroYieldRepository.findAll()).thenReturn(Flux.just(snap));
        when(macroYieldRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snap));
        when(macroYieldRepository.findById(id)).thenReturn(Mono.just(snap));

        StepVerifier.create(queryService.listMacroYieldSnapshots(null))
                .assertNext(s -> assertThat(s.getUsCorporateBondEffectiveYield()).isEqualTo(new BigDecimal("5.25")))
                .verifyComplete();

        StepVerifier.create(queryService.getLatestMacroYieldSnapshot())
                .assertNext(s -> assertThat(s.getId()).isEqualTo(id))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should query DCA ranks, dividends, and corporate actions")
    void shouldQueryDcaDividendsAndSplits() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        DcaPopularityRank dca = new DcaPopularityRank(id, UUID.randomUUID(), "0050", 2026, 8, 1, 1280000);
        DividendAnnouncement div = new DividendAnnouncement(id, UUID.randomUUID(), "0050", now, now.plusDays(30), new BigDecimal("1.5"), TaxTag.DOMESTIC_54C);
        CorporateAction ca = new CorporateAction(id, UUID.randomUUID(), "0050", CorporateActionType.SPLIT, now, 4, 1);

        when(dcaRankRepository.findByRankingYearAndRankingMonthOrderByRankPositionAsc(2026, 8)).thenReturn(Flux.just(dca));
        when(dividendRepository.findByTickerOrderByExDateDesc("0050")).thenReturn(Flux.just(div));
        when(corporateActionRepository.findByTickerOrderByEffectiveDateDesc("0050")).thenReturn(Flux.just(ca));

        StepVerifier.create(queryService.getTop20DcaRanks(2026, 8))
                .assertNext(r -> assertThat(r.getRankPosition()).isEqualTo(1))
                .verifyComplete();

        StepVerifier.create(queryService.listDividendAnnouncements(new DividendAnnouncementFilterInput("0050", null, null)))
                .assertNext(d -> assertThat(d.getDividendPerShare()).isEqualTo(new BigDecimal("1.5")))
                .verifyComplete();

        StepVerifier.create(queryService.listCorporateActions(new CorporateActionFilterInput("0050", null)))
                .assertNext(c -> assertThat(c.getSplitToShares()).isEqualTo(4))
                .verifyComplete();
    }
}
