package com.alphaharvester.application.port.out;

import com.alphaharvester.domain.entity.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ExternalMarketDataPort {

    /**
     * Fetches ETF master universe metadata from TWSE OpenAPI.
     */
    Flux<GlobalAssetMetadata> fetchEtfMasterUniverse();

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
    Flux<MarketDailyQuote> fetchDailyQuotes(List<String> monitoredTickers);

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
     * Fetches multi-day historical bars for an ETF or benchmark index from Yahoo Finance
     * to backfill missing trading days (e.g. range = "1mo", "3mo", "1y").
     */
    Flux<MarketDailyQuote> fetchHistoricalQuotes(String ticker, String range);
}
