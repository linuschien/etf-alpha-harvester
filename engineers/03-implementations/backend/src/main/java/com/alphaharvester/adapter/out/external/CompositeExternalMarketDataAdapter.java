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
    private final FredPublicMarketDataClient fredClient;

    public CompositeExternalMarketDataAdapter(TwseMarketDataClient twseClient,
                                              TpexMarketDataClient tpexClient,
                                              YahooFinanceClient yahooFinanceClient,
                                              CnnSentimentClient cnnSentimentClient,
                                              FredPublicMarketDataClient fredClient) {
        this.twseClient = twseClient;
        this.tpexClient = tpexClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.cnnSentimentClient = cnnSentimentClient;
        this.fredClient = fredClient;
    }

    @Override
    public Flux<GlobalAssetMetadata> fetchEtfMasterUniverse() {
        log.info("Fetching real ETF master universe from TWSE OpenAPI...");
        return twseClient.fetchEtfMasterUniverse();
    }

    @Override
    public Flux<MarketDailyQuote> fetchTaiwanEtfDailyQuotes(List<String> monitoredTickers) {
        log.info("Fetching daily quotes for Taiwan ETFs from TWSE and TPEx with MIS NAV enrichment...");
        Flux<MarketDailyQuote> twseQuotes = twseClient.fetchTwseDailyQuotes();
        Flux<MarketDailyQuote> tpexQuotes = tpexClient.fetchTpexDailyQuotes();
        Flux<MarketDailyQuote> primaryQuotes = Flux.concat(twseQuotes, tpexQuotes);

        Mono<List<MarketDailyQuote>> primaryListMono = primaryQuotes.collectList();

        Mono<List<MarketDailyQuote>> fallbackQuotesMono = primaryListMono.flatMap(primaryList -> {
            Set<String> acquiredTickers = new HashSet<>();
            for (MarketDailyQuote q : primaryList) {
                acquiredTickers.add(q.getTicker());
            }

            List<String> missingTickers = monitoredTickers != null ? monitoredTickers.stream()
                    .filter(t -> !acquiredTickers.contains(t))
                    .toList() : List.of();

            if (!missingTickers.isEmpty()) {
                log.warn("Detected {} monitored ETF(s) missing from TWSE/TPEx daily reports: {}. Recovering from Yahoo Finance...",
                        missingTickers.size(), missingTickers);
                return Flux.fromIterable(missingTickers)
                        .flatMap(yahooFinanceClient::fetchTaiwanEtfQuote)
                        .collectList();
            }
            return Mono.just(List.of());
        });

        Flux<MarketDailyQuote> mergedQuotes = primaryListMono
                .flatMapMany(primaryList -> fallbackQuotesMono.flatMapMany(fallbackList ->
                        Flux.concat(
                                Flux.fromIterable(primaryList),
                                Flux.fromIterable(fallbackList)
                        )
                ));

        return twseClient.fetchMisNavData()
                .flatMapMany(navMap -> mergedQuotes.map(quote -> {
                    if (navMap.containsKey(quote.getTicker())) {
                        var nav = navMap.get(quote.getTicker());
                        quote.setNetAssetValue(nav.nav());
                        quote.setDiscountPremiumPercentage(nav.discountPremiumPct());
                    }
                    return quote;
                }));
    }

    @Override
    public Flux<MarketDailyQuote> fetchBenchmarkQuotes(String range) {
        String effectiveRange = (range != null && !range.isBlank()) ? range : "1mo";
        log.info("Fetching quotes for {} global benchmark indices from Yahoo Finance (range: {})...",
                BENCHMARK_SYMBOLS.size(), effectiveRange);
        return Flux.fromIterable(BENCHMARK_SYMBOLS)
                .flatMap(symbol -> yahooFinanceClient.fetchHistoricalQuotes(symbol, effectiveRange));
    }

    @Override
    public Mono<MarketDailyQuote> fetchCnnSentimentQuote() {
        log.info("Fetching CNN Fear & Greed sentiment index...");
        return cnnSentimentClient.fetchFearAndGreedIndex();
    }

    @Override
    public Flux<MarketDailyQuote> fetchDailyQuotes(List<String> monitoredTickers) {
        log.info("Fetching composite daily quotes (ETFs, Benchmarks, CNN)...");
        return Flux.concat(
                fetchTaiwanEtfDailyQuotes(monitoredTickers),
                fetchBenchmarkQuotes("1mo"),
                fetchCnnSentimentQuote().flux()
        );
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
        log.info("Fetching real macroeconomic yields from St. Louis Fed FRED (BAMLC0A0CMEY, DGS10, DGS20, T10Y2Y)...");
        return fredClient.fetchLatestMacroYield();
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
