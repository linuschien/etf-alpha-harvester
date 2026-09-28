package com.alphaharvester.application.port.out;

import com.alphaharvester.domain.entity.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ExternalMarketDataPort {

    /**
     * Fetches ETF master universe metadata from TWSE OpenAPI and TPEx OpenData.
     */
    Flux<GlobalAssetMetadata> fetchEtfMasterUniverse();

    /**
     * Fetches current AUM (in TWD) for Taiwan ETFs based on outstanding units and NAV.
     * Used dynamically when calculating monthly Top List rankings.
     */
    default Mono<java.util.Map<String, java.math.BigDecimal>> fetchCurrentAumMap() {
        return Mono.just(java.util.Map.of());
    }

    /**
     * Fetches daily market closing quotes for TWSE & TPEx ETFs and Yahoo Finance benchmarks.
     */
    default Flux<MarketDailyQuote> fetchDailyQuotes() {
        return fetchDailyQuotes(List.of());
    }

    /**
     * Fetches daily market closing quotes with automatic fallback to Yahoo Finance for any monitored tickers
     * missing from TWSE / TPEx daily reports.
     */
    default Flux<MarketDailyQuote> fetchDailyQuotes(List<String> monitoredTickers) {
        return Flux.concat(
                fetchTaiwanEtfDailyQuotes(monitoredTickers),
                fetchBenchmarkQuotes("1mo"),
                fetchCnnSentimentQuote().flux()
        );
    }

    /**
     * Fetches daily market closing quotes for TWSE & TPEx ETFs, enriched with MIS NAV,
     * with automatic fallback to Yahoo Finance for missing tickers.
     */
    Flux<MarketDailyQuote> fetchTaiwanEtfDailyQuotes(List<String> monitoredTickers);

    /**
     * Fetches quotes for 8 global benchmark indices from Yahoo Finance over a given range.
     */
    Flux<MarketDailyQuote> fetchBenchmarkQuotes(String range);

    /**
     * Fetches CNN Fear & Greed sentiment score.
     */
    Mono<MarketDailyQuote> fetchCnnSentimentQuote();

    /**
     * Fetches macroeconomic treasury yields from Yahoo Finance (Zero API key required).
     */
    Mono<MacroYieldSnapshot> fetchLatestMacroYield();

    /**
     * Fetches regular quota (DCA) Top 20 ETF rankings from TWSE OpenAPI.
     */
    Flux<DcaPopularityRank> fetchDcaPopularityRanks(int year, int month);

    /**
     * Fetches dividend distributions for a specific ETF ticker from Yahoo Finance events.
     */
    Flux<DividendAnnouncement> fetchDividendAnnouncements(String ticker);

    /**
     * Fetches stock split corporate actions for a specific ETF ticker from Yahoo Finance events.
     */
    Flux<CorporateAction> fetchCorporateActions(String ticker);

    /**
     * Data carrier for combined dividend distributions and stock split corporate actions.
     */
    record DividendsAndSplits(List<DividendAnnouncement> dividends, List<CorporateAction> splits) {}

    /**
     * Concurrently fetches dividend distributions and stock split corporate actions
     * for a specific ETF ticker in a single external request.
     */
    default Mono<DividendsAndSplits> fetchDividendsAndSplits(String ticker) {
        Flux<DividendAnnouncement> divFlux = fetchDividendAnnouncements(ticker);
        Flux<CorporateAction> splitFlux = fetchCorporateActions(ticker);
        Mono<List<DividendAnnouncement>> divMono = (divFlux != null) ? divFlux.collectList() : Mono.just(List.of());
        Mono<List<CorporateAction>> splitMono = (splitFlux != null) ? splitFlux.collectList() : Mono.just(List.of());
        return Mono.zip(divMono, splitMono, DividendsAndSplits::new);
    }

    /**
     * Fetches multi-day historical bars for an ETF or benchmark index from Yahoo Finance
     * to backfill missing trading days (e.g. range = "1mo", "3mo", "1y").
     */
    Flux<MarketDailyQuote> fetchHistoricalQuotes(String ticker, String range);
}
