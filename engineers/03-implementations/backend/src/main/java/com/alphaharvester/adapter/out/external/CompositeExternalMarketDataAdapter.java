package com.alphaharvester.adapter.out.external;

import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.domain.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CompositeExternalMarketDataAdapter implements ExternalMarketDataPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeExternalMarketDataAdapter.class);

    private static final List<String> BENCHMARK_SYMBOLS = List.of(
            "^TWII", "^GSPC", "^NDX", "^SOX", "^N225", "^VIX", "^VXN", "^MOVE"
    );

    private final TwseMarketDataClient twseClient;
    private final TpexMarketDataClient tpexClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final CnnSentimentClient cnnSentimentClient;

    public CompositeExternalMarketDataAdapter(TwseMarketDataClient twseClient,
                                              TpexMarketDataClient tpexClient,
                                              YahooFinanceClient yahooFinanceClient,
                                              CnnSentimentClient cnnSentimentClient) {
        this.twseClient = twseClient;
        this.tpexClient = tpexClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.cnnSentimentClient = cnnSentimentClient;
    }

    @Override
    public Flux<GlobalAssetMetadata> fetchEtfMasterUniverse() {
        log.info("Fetching real ETF master universe from TWSE OpenAPI...");
        return twseClient.fetchEtfMasterUniverse();
    }

    @Override
    public Flux<MarketDailyQuote> fetchDailyQuotes(List<String> monitoredTickers) {
        log.info("Fetching real daily quotes from TWSE, TPEx, Yahoo Finance, and CNN Fear & Greed...");

        Flux<MarketDailyQuote> twseQuotes = twseClient.fetchTwseDailyQuotes();
        Flux<MarketDailyQuote> tpexQuotes = tpexClient.fetchTpexDailyQuotes();
        Flux<MarketDailyQuote> primaryQuotes = Flux.concat(twseQuotes, tpexQuotes);

        // Collect primary quotes and check if any monitored ETF is missing
        Mono<List<MarketDailyQuote>> primaryListMono = primaryQuotes.collectList();

        Mono<List<MarketDailyQuote>> fallbackQuotesMono = primaryListMono.flatMap(primaryList -> {
            Set<String> acquiredTickers = new HashSet<>();
            for (MarketDailyQuote q : primaryList) {
                acquiredTickers.add(q.getTicker());
            }

            List<String> missingTickers = monitoredTickers.stream()
                    .filter(t -> !acquiredTickers.contains(t))
                    .toList();

            if (!missingTickers.isEmpty()) {
                log.warn("Detected {} monitored ETF(s) missing from TWSE/TPEx daily reports: {}. Recovering from Yahoo Finance...",
                        missingTickers.size(), missingTickers);
                return Flux.fromIterable(missingTickers)
                        .flatMap(yahooFinanceClient::fetchTaiwanEtfQuote)
                        .collectList();
            }
            return Mono.just(List.of());
        });

        // 8 global benchmarks (past 1 month to catch up any missing days) + CNN Fear & Greed
        Flux<MarketDailyQuote> benchmarkQuotes = Flux.fromIterable(BENCHMARK_SYMBOLS)
                .flatMap(symbol -> yahooFinanceClient.fetchHistoricalQuotes(symbol, "1mo"));

        Mono<MarketDailyQuote> fearGreedQuote = cnnSentimentClient.fetchFearAndGreedIndex();

        Flux<MarketDailyQuote> allQuotes = primaryListMono
                .flatMapMany(primaryList -> fallbackQuotesMono.flatMapMany(fallbackList ->
                        Flux.concat(
                                Flux.fromIterable(primaryList),
                                Flux.fromIterable(fallbackList),
                                benchmarkQuotes,
                                fearGreedQuote
                        )
                ));

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
    public Flux<MarketDailyQuote> fetchHistoricalQuotes(String ticker, String range) {
        log.info("Fetching historical quotes for ticker '{}' (range: {})...", ticker, range);
        if (ticker.startsWith("^")) {
            return yahooFinanceClient.fetchHistoricalQuotes(ticker, range);
        } else if ("FEAR_GREED".equals(ticker)) {
            return cnnSentimentClient.fetchFearAndGreedIndex().flux();
        } else {
            return yahooFinanceClient.fetchTaiwanEtfHistoricalQuotes(ticker, range);
        }
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
