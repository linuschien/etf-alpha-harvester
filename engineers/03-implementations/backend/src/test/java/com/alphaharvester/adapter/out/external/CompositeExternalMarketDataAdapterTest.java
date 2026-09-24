package com.alphaharvester.adapter.out.external;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeExternalMarketDataAdapterTest {

    @Mock private TwseMarketDataClient twseClient;
    @Mock private TpexMarketDataClient tpexClient;
    @Mock private YahooFinanceClient yahooFinanceClient;
    @Mock private CnnSentimentClient cnnSentimentClient;
    @Mock private FredPublicMarketDataClient fredClient;

    private CompositeExternalMarketDataAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CompositeExternalMarketDataAdapter(twseClient, tpexClient, yahooFinanceClient, cnnSentimentClient, fredClient);
    }

    @Test
    @DisplayName("Should delegate fetchEtfMasterUniverse to TwseMarketDataClient")
    void shouldDelegateFetchEtfMasterUniverse() {
        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", LocalDateTime.now(),
                "臺灣50指數",
                new BigDecimal("420000000000"), CandidateAssetClass.CORE,
                DistributionFrequency.SEMI_ANNUAL, 1, LocalDateTime.now(), LocalDateTime.now(), null
        );
        when(twseClient.fetchEtfMasterUniverse()).thenReturn(Flux.just(asset));

        StepVerifier.create(adapter.fetchEtfMasterUniverse())
                .assertNext(res -> assertThat(res.getTicker()).isEqualTo("0050"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should combine quotes, recover missing ETF from Yahoo Finance, include CNN Fear & Greed, and enrich NAV")
    void shouldCombineQuotesAndRecoverMissingAndIncludeFearGreed() {
        LocalDateTime now = LocalDateTime.now();
        MarketDailyQuote twseQuote = new MarketDailyQuote(
                null, null, null, "0050", now,
                new BigDecimal("185.0"), new BigDecimal("189.0"), new BigDecimal("184.0"),
                new BigDecimal("188.0"), 1000000L, new BigDecimal("188000000"), null, null
        );
        MarketDailyQuote tpexQuote = new MarketDailyQuote(
                null, null, null, "00679B", now,
                new BigDecimal("30.0"), new BigDecimal("31.0"), new BigDecimal("29.5"),
                new BigDecimal("30.5"), 500000L, new BigDecimal("15250000"), null, null
        );
        MarketDailyQuote recovered006208 = new MarketDailyQuote(
                null, null, null, "006208", now,
                new BigDecimal("113.0"), new BigDecimal("115.0"), new BigDecimal("112.5"),
                new BigDecimal("114.5"), 800000L, new BigDecimal("91600000"), null, null
        );
        MarketDailyQuote benchmarkQuote = new MarketDailyQuote(
                null, null, null, "^TWII", now,
                new BigDecimal("22500.0"), new BigDecimal("22800.0"), new BigDecimal("22450.0"),
                new BigDecimal("22750.0"), 500000000L, BigDecimal.ZERO, null, null
        );
        MarketDailyQuote fearGreedQuote = new MarketDailyQuote(
                null, null, null, "FEAR_GREED", now,
                new BigDecimal("45.5"), new BigDecimal("45.5"), new BigDecimal("45.5"),
                new BigDecimal("45.5"), 0L, BigDecimal.ZERO, null, null
        );

        when(twseClient.fetchTwseDailyQuotes()).thenReturn(Flux.just(twseQuote));
        when(tpexClient.fetchTpexDailyQuotes()).thenReturn(Flux.just(tpexQuote));

        // 006208 is monitored but missing from TWSE/TPEx; Yahoo recovery will be triggered
        when(yahooFinanceClient.fetchTaiwanEtfQuote("006208")).thenReturn(Mono.just(recovered006208));

        // Yahoo benchmarks (past 1 month)
        when(yahooFinanceClient.fetchHistoricalQuotes(anyString(), anyString())).thenReturn(Flux.empty());
        when(yahooFinanceClient.fetchHistoricalQuotes(eq("^TWII"), anyString())).thenReturn(Flux.just(benchmarkQuote));

        // CNN Fear & Greed
        when(cnnSentimentClient.fetchFearAndGreedIndex()).thenReturn(Mono.just(fearGreedQuote));

        Map<String, TwseMarketDataClient.NavSnapshot> navMap = Map.of(
                "0050", new TwseMarketDataClient.NavSnapshot(new BigDecimal("188.20"), new BigDecimal("-0.11"), 2200000000L)
        );
        when(twseClient.fetchMisNavData()).thenReturn(Mono.just(navMap));

        // Pass monitored tickers: ["0050", "00679B", "006208"]
        StepVerifier.create(adapter.fetchDailyQuotes(List.of("0050", "00679B", "006208")))
                .assertNext(q -> {
                    assertThat(q.getTicker()).isEqualTo("0050");
                    assertThat(q.getNetAssetValue()).isEqualTo(new BigDecimal("188.20"));
                })
                .assertNext(q -> assertThat(q.getTicker()).isEqualTo("00679B"))
                .assertNext(q -> assertThat(q.getTicker()).isEqualTo("006208")) // successfully recovered from Yahoo!
                .assertNext(q -> assertThat(q.getTicker()).isEqualTo("^TWII"))
                .assertNext(q -> {
                    assertThat(q.getTicker()).isEqualTo("FEAR_GREED");
                    assertThat(q.getClosePrice()).isEqualTo(new BigDecimal("45.5"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should delegate fetchLatestMacroYield to FredPublicMarketDataClient")
    void shouldDelegateFetchLatestMacroYield() {
        MacroYieldSnapshot snap = new MacroYieldSnapshot(
                UUID.randomUUID(), LocalDateTime.now(), new BigDecimal("5.69"),
                new BigDecimal("4.96"), new BigDecimal("5.33"), new BigDecimal("0.25")
        );
        when(fredClient.fetchLatestMacroYield()).thenReturn(Mono.just(snap));

        StepVerifier.create(adapter.fetchLatestMacroYield())
                .assertNext(res -> assertThat(res.getUsCorporateBondEffectiveYield()).isEqualTo(new BigDecimal("5.69")))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should delegate DCA rankings, dividends, and splits")
    void shouldDelegateDcaAndCorporateActions() {
        DcaPopularityRank rank = new DcaPopularityRank(null, null, "0050", 2026, 8, 1, 1280000);
        DividendAnnouncement div = new DividendAnnouncement(null, null, "0050", LocalDateTime.now(), LocalDateTime.now().plusDays(30), new BigDecimal("1.5"), TaxTag.DOMESTIC_54C);
        CorporateAction split = new CorporateAction(null, null, "0050", CorporateActionType.SPLIT, LocalDateTime.now(), 4, 1);

        when(twseClient.fetchDcaRankings(2026, 8)).thenReturn(Flux.just(rank));
        when(yahooFinanceClient.fetchDividends("0050")).thenReturn(Flux.just(div));
        when(yahooFinanceClient.fetchSplits("0050")).thenReturn(Flux.just(split));

        StepVerifier.create(adapter.fetchDcaPopularityRanks(2026, 8))
                .assertNext(r -> assertThat(r.getRankPosition()).isEqualTo(1))
                .verifyComplete();

        StepVerifier.create(adapter.fetchDividendAnnouncements("0050"))
                .assertNext(d -> assertThat(d.getDividendPerShare()).isEqualTo(new BigDecimal("1.5")))
                .verifyComplete();

        StepVerifier.create(adapter.fetchCorporateActions("0050"))
                .assertNext(s -> assertThat(s.getSplitToShares()).isEqualTo(4))
                .verifyComplete();
    }
}
