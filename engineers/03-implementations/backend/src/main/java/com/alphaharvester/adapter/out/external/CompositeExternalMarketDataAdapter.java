package com.alphaharvester.adapter.out.external;

import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.domain.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class CompositeExternalMarketDataAdapter implements ExternalMarketDataPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeExternalMarketDataAdapter.class);

    private static final List<String> BENCHMARK_SYMBOLS = List.of(
            "^TWII", "^GSPC", "^NDX", "^SOX", "^N225", "^VIX", "^VXN", "^MOVE"
    );

    private final TwseMarketDataClient twseClient;
    private final TpexMarketDataClient tpexClient;
    private final YahooFinanceClient yahooFinanceClient;

    public CompositeExternalMarketDataAdapter(TwseMarketDataClient twseClient,
                                              TpexMarketDataClient tpexClient,
                                              YahooFinanceClient yahooFinanceClient) {
        this.twseClient = twseClient;
        this.tpexClient = tpexClient;
        this.yahooFinanceClient = yahooFinanceClient;
    }

    @Override
    public Flux<GlobalAssetMetadata> fetchEtfMasterUniverse() {
        log.info("Fetching real ETF master universe from TWSE OpenAPI...");
        return twseClient.fetchEtfMasterUniverse();
    }

    @Override
    public Flux<MarketDailyQuote> fetchDailyQuotes() {
        log.info("Fetching real daily quotes from TWSE, TPEx, and Yahoo Finance...");
        Flux<MarketDailyQuote> twseQuotes = twseClient.fetchTwseDailyQuotes();
        Flux<MarketDailyQuote> tpexQuotes = tpexClient.fetchTpexDailyQuotes();
        Flux<MarketDailyQuote> benchmarkQuotes = Flux.fromIterable(BENCHMARK_SYMBOLS)
                .flatMap(yahooFinanceClient::fetchBenchmarkQuote);

        Flux<MarketDailyQuote> allQuotes = Flux.concat(twseQuotes, tpexQuotes, benchmarkQuotes);

        return twseClient.fetchMisNavData()
                .flatMapMany(navMap -> allQuotes.map(quote -> {
                    if (navMap.containsKey(quote.getTicker())) {
                        var nav = navMap.get(quote.getTicker());
                        quote.setNetAssetValue(nav.nav());
                        quote.setDiscountPremiumPercentage(nav.discountPremiumPct());
                    }
                    return quote;
                }));
    }

    @Override
    public Mono<MacroYieldSnapshot> fetchLatestMacroYield() {
        log.info("Fetching real macroeconomic yields from Yahoo Finance (^TNX, ^TYX)...");
        return yahooFinanceClient.fetchMacroYields();
    }

    @Override
    public Flux<DcaPopularityRank> fetchDcaPopularityRanks(int year, int month) {
        log.info("Fetching real DCA Top 20 rankings from TWSE OpenAPI for {}-{}...", year, month);
        return twseClient.fetchDcaRankings(year, month);
    }

    @Override
    public Flux<DividendAnnouncement> fetchDividendAnnouncements(String ticker) {
        log.info("Fetching real dividend history from Yahoo Finance for '{}'...", ticker);
        return yahooFinanceClient.fetchDividends(ticker);
    }

    @Override
    public Flux<CorporateAction> fetchCorporateActions(String ticker) {
        log.info("Fetching real stock splits from Yahoo Finance for '{}'...", ticker);
        return yahooFinanceClient.fetchSplits(ticker);
    }
}

