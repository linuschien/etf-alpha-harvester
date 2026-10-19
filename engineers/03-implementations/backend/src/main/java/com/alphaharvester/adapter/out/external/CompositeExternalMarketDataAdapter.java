package com.alphaharvester.adapter.out.external;

import com.alphaharvester.application.port.out.ExternalMarketDataPort;
import com.alphaharvester.application.util.SyncRangeUtil;
import com.alphaharvester.domain.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CompositeExternalMarketDataAdapter implements ExternalMarketDataPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeExternalMarketDataAdapter.class);

    private static final List<String> BENCHMARK_SYMBOLS = List.of(
            "^TWII", "^GSPC", "^NDX", "^SOX", "^N225", "^VIX", "^VXN", "^MOVE"
    );
    private static final String DEFAULT_BENCHMARK_RANGE = "1mo";

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
        log.info("Fetching real ETF master universe from TWSE OpenAPI, TPEx OpenData, and enriching with TWSE MIS NAV...");
        Mono<java.util.Map<String, TwseMarketDataClient.NavSnapshot>> navMono = Mono.empty();
        if (twseClient != null) {
            try {
                var res = twseClient.fetchMisNavData();
                if (res != null) {
                    navMono = res.onErrorReturn(java.util.Collections.emptyMap());
                }
            } catch (Exception ignored) {}
        }
        return navMono.defaultIfEmpty(java.util.Collections.emptyMap())
                .flatMapMany(navMap -> {
                    Flux<GlobalAssetMetadata> twse = twseClient != null ? twseClient.fetchEtfMasterUniverse() : Flux.empty();
                    Flux<GlobalAssetMetadata> tpex = tpexClient != null ? tpexClient.fetchTpexEtfMasterUniverse() : Flux.empty();
                    return Flux.concat(twse, tpex).map(asset -> {
                        if (navMap != null && asset.getTicker() != null) {
                            var snap = navMap.get(asset.getTicker());
                            if (snap != null) {
                                asset.setSharesOutstanding(snap.sharesOutstanding());
                                asset.setNetAssetValue(snap.nav());
                                if (snap.nav() != null && snap.sharesOutstanding() > 0) {
                                    asset.setFundSizeTwd(snap.nav()
                                            .multiply(java.math.BigDecimal.valueOf(snap.sharesOutstanding()))
                                            .setScale(2, java.math.RoundingMode.HALF_UP));
                                }
                            }
                        }
                        return asset;
                    });
                });
    }

    @Override
    public Mono<java.util.Map<String, java.math.BigDecimal>> fetchCurrentAumMap() {
        log.info("Fetching current AUM map from TWSE MIS for monthly Top List evaluation...");
        return twseClient.fetchMisNavData()
                .flatMap(navMap -> {
                    java.util.Map<String, java.math.BigDecimal> aumMap = new java.util.HashMap<>();
                    for (var entry : navMap.entrySet()) {
                        var snap = entry.getValue();
                        if (snap != null && snap.nav() != null && snap.sharesOutstanding() > 0) {
                            java.math.BigDecimal aum = snap.nav()
                                    .multiply(java.math.BigDecimal.valueOf(snap.sharesOutstanding()))
                                    .setScale(2, java.math.RoundingMode.HALF_UP);
                            aumMap.put(entry.getKey(), aum);
                        }
                    }
                    if (aumMap.isEmpty()) {
                        return Mono.error(new IllegalStateException("TWSE MIS returned empty or invalid NAV data; unable to compute ETF AUM"));
                    }
                    return Mono.just(aumMap);
                });
    }

    @Override
    public Flux<MarketDailyQuote> fetchTaiwanEtfDailyQuotes(List<String> monitoredTickers) {
        log.info("Fetching daily quotes for Taiwan ETFs from TWSE and TPEx...");
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
                        .flatMap(yahooFinanceClient::fetchTaiwanEtfQuote, 4)
                        .collectList();
            }
            return Mono.just(List.of());
        });

        return primaryListMono
                .flatMapMany(primaryList -> fallbackQuotesMono.flatMapMany(fallbackList ->
                        Flux.concat(
                                Flux.fromIterable(primaryList),
                                Flux.fromIterable(fallbackList)
                        )
                ));
    }

    @Override
    public Flux<MarketDailyQuote> fetchBenchmarkQuotes(String range) {
        String effectiveRange = (range != null && !range.isBlank()) ? range : DEFAULT_BENCHMARK_RANGE;
        log.info("Fetching quotes for {} global benchmark indices from Yahoo Finance (range: {})...",
                BENCHMARK_SYMBOLS.size(), effectiveRange);
        return Flux.fromIterable(BENCHMARK_SYMBOLS)
                .flatMap(symbol -> yahooFinanceClient.fetchHistoricalQuotes(symbol, effectiveRange), 4);
    }

    @Override
    public Flux<MarketDailyQuote> fetchHistoricalQuotes(String ticker, String range) {
        log.info("Fetching historical quotes for ticker '{}' (range: {})...", ticker, range);
        if (ticker.startsWith("^")) {
            return yahooFinanceClient.fetchHistoricalQuotes(ticker, range);
        } else if ("FEAR_GREED".equals(ticker)) {
            LocalDate startDate = SyncRangeUtil.deriveStartDate(LocalDate.now(), range);
            return cnnSentimentClient.fetchHistoricalFearAndGreedIndex(startDate.toString());
        } else {
            return yahooFinanceClient.fetchTaiwanEtfHistoricalQuotes(ticker, range);
        }
    }

    @Override
    public Flux<MacroYieldSnapshot> fetchHistoricalMacroYields(LocalDate startDate, LocalDate endDate) {
        log.info("Fetching real historical macroeconomic yields from St. Louis Fed FRED ({} to {})...", startDate, endDate);
        return fredClient.fetchHistoricalMacroYields(startDate, endDate);
    }

    @Override
    public Flux<DcaPopularityRank> fetchDcaPopularityRanks(int year, int month) {
        log.info("Fetching real DCA Top 20 rankings from TWSE OpenAPI for {}-{}...", year, month);
        return twseClient.fetchDcaRankings(year, month);
    }

    @Override
    public Mono<DividendsAndSplits> fetchDividendsAndSplits(String ticker, String range) {
        log.info("Fetching real dividend history and stock splits concurrently from Yahoo Finance for '{}' (range: {})...", ticker, range);
        return yahooFinanceClient.fetchDividendsAndSplits(ticker, range);
    }
}
