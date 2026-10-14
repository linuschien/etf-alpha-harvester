package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.CorporateActionRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.domain.entity.CorporateAction;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.math.FinancialMetricsCalculator;
import com.alphaharvester.domain.math.TechnicalIndicatorCalculator;
import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing monthly cached technical indicator quotes backed by Ehcache.
 *
 * Implements:
 * 1. Monthly chunk key format: "{ticker}:{YYYY-MM}"
 * 2. Immutable Historical Months: Closed months (ym &lt; currentYm) are permanent once cached,
 *    enjoying the (13 - Delta) months dynamic TTL without being overwritten by daily updates.
 * 3. Three distinct execution paths:
 *    - Path 1: 100% Cache Hit (zero DB queries, zero recomputation)
 *    - Path 2: Isolated Current Month Incremental Refresh (updates ONLY current month, historical untouched)
 *    - Path 3: Cold Start / Historical Backfill (full lookback initialization for missing historical months)
 * 4. Corporate action split adjustment for historical continuity across stock split dates
 * 5. Head and tail date boundary slicing across multi-month windows
 */
@Service
public class MonthlyQuoteCacheService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyQuoteCacheService.class);

    private final Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache;
    private final MarketDailyQuoteRepository quoteRepository;
    private final CorporateActionRepository corporateActionRepository;

    public MonthlyQuoteCacheService(Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache,
                                    MarketDailyQuoteRepository quoteRepository,
                                    CorporateActionRepository corporateActionRepository) {
        this.monthlyQuoteCache = monthlyQuoteCache;
        this.quoteRepository = quoteRepository;
        this.corporateActionRepository = corporateActionRepository;
    }

    /**
     * Retrieves enriched daily quotes (with MA20/60/120/240 and BB) for a specific date range.
     * Reuses monthly cached chunks whenever available with strict historical immutability.
     *
     * @param ticker Asset or benchmark ticker symbol (e.g. "0050", "^TWII")
     * @param start  Start timestamp inclusive
     * @param end    End timestamp inclusive
     * @return Chronological flux of quotes with technical indicators populated
     */
    public Flux<MarketDailyQuote> getQuoteTimeSeries(String ticker, LocalDateTime start, LocalDateTime end) {
        if (ticker == null || start == null || end == null || start.isAfter(end)) {
            return Flux.empty();
        }

        YearMonth startYm = YearMonth.from(start);
        YearMonth endYm = YearMonth.from(end);
        YearMonth currentYm = YearMonth.now();

        List<YearMonth> requiredMonths = new ArrayList<>();
        YearMonth cursor = startYm;
        while (!cursor.isAfter(endYm)) {
            requiredMonths.add(cursor);
            cursor = cursor.plusMonths(1);
        }

        List<YearMonth> historicalMonths = requiredMonths.stream()
                .filter(ym -> ym.isBefore(currentYm))
                .toList();
        boolean includesCurrentMonth = requiredMonths.contains(currentYm);

        // Check cache for all historical months (closed months)
        List<YearMonth> missingHistoricalMonths = new ArrayList<>();
        for (YearMonth ym : historicalMonths) {
            String key = buildCacheKey(ticker, ym);
            MonthlyQuoteCacheEntry entry = monthlyQuoteCache.get(key);
            if (entry == null) {
                missingHistoricalMonths.add(ym);
            }
        }

        // ── PATH 3: Cold Start / Historical Backfill ──────────────────────────────────
        if (!missingHistoricalMonths.isEmpty()) {
            log.info("[QuoteCache: PATH 3 - Cold Start/Backfill] Ticker {}: missing {} historical months (e.g. {} out of {} required). Triggering full 2-year lookback reload.",
                    ticker, missingHistoricalMonths.size(), missingHistoricalMonths.get(0), historicalMonths.size());
            return executeColdStartFullReload(ticker, start, end, requiredMonths);
        }

        // ── PATH 1: Historical months only (current month not requested) ─────────────
        if (!includesCurrentMonth) {
            log.debug("[QuoteCache: PATH 1 - Cache Hit] Ticker {}: all {} requested historical months found in cache, current month not in scope.",
                    ticker, requiredMonths.size());
            return extractAndSlice(ticker, requiredMonths, start, end);
        }

        // ── Ongoing current month check (Path 1 vs Path 2) ───────────────────────────
        String currentMonthKey = buildCacheKey(ticker, currentYm);
        MonthlyQuoteCacheEntry currentEntry = monthlyQuoteCache.get(currentMonthKey);

        return quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker)
                .defaultIfEmpty(new MarketDailyQuote())
                .flatMapMany(latestDbQuote -> {
                    boolean isStale;
                    LocalDateTime latestDbTradeDate = latestDbQuote.getTradeDate();

                    if (currentEntry == null) {
                        isStale = true;
                    } else if (latestDbTradeDate == null) {
                        isStale = false;
                    } else {
                        YearMonth latestDbYm = YearMonth.from(latestDbTradeDate);
                        if (latestDbYm.isBefore(currentYm)) {
                            // DB has no records for current ongoing month yet; cache is up-to-date
                            isStale = false;
                        } else if (currentEntry.getLatestTradeDate() == null) {
                            // DB now has records in ongoing month, but cache had empty entry
                            isStale = true;
                        } else {
                            isStale = latestDbTradeDate.isAfter(currentEntry.getLatestTradeDate());
                        }
                    }

                    if (!isStale) {
                        // ── PATH 1: 100% Cache Hit ──────────────────────────────────────────
                        log.debug("[QuoteCache: PATH 1 - Cache Hit] Ticker {}: all {} historical months cached, current month {} is up to date (latestTradeDate: {}).",
                                ticker, historicalMonths.size(), currentYm, currentEntry != null ? currentEntry.getLatestTradeDate() : "N/A");
                        return extractAndSlice(ticker, requiredMonths, start, end);
                    }

                    // ── PATH 2: Current Month Isolated Incremental Refresh ──────────────
                    log.info("[QuoteCache: PATH 2 - Incremental Refresh] Ticker {}: historical {} months fully cached; refreshing only current month {} (cached latest: {}, DB latest: {}).",
                            ticker, historicalMonths.size(), currentYm,
                            currentEntry != null ? currentEntry.getLatestTradeDate() : "none",
                            latestDbTradeDate != null ? latestDbTradeDate : "none");

                    return executeCurrentMonthIncrementalRefresh(ticker, currentYm, requiredMonths, start, end);
                });
    }

    /**
     * Executes Path 2: Refreshes ONLY the ongoing current month, maintaining historical continuity
     * without modifying, re-querying, or overwriting any closed historical month in the cache.
     */
    private Flux<MarketDailyQuote> executeCurrentMonthIncrementalRefresh(
            String ticker, YearMonth currentYm, List<YearMonth> requiredMonths,
            LocalDateTime start, LocalDateTime end) {

        LocalDateTime currentMonthStart = currentYm.atDay(1).atStartOfDay();
        LocalDateTime lookbackStart = currentMonthStart.minusYears(1);

        Mono<List<MarketDailyQuote>> quotesMono = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, lookbackStart, end)
                .collectList();

        Mono<List<CorporateAction>> splitsMono = (corporateActionRepository != null)
                ? corporateActionRepository.findByTicker(ticker).collectList()
                : Mono.just(Collections.emptyList());

        return Mono.zip(quotesMono, splitsMono)
                .flatMapMany(tuple -> {
                    List<MarketDailyQuote> rawQuotes = tuple.getT1();
                    List<CorporateAction> splits = tuple.getT2();

                    if (rawQuotes.isEmpty()) {
                        monthlyQuoteCache.put(buildCacheKey(ticker, currentYm), MonthlyQuoteCacheEntry.builder()
                                .ticker(ticker)
                                .yearMonth(currentYm.toString())
                                .latestTradeDate(null)
                                .quotes(Collections.emptyList())
                                .build());
                        return extractAndSlice(ticker, requiredMonths, start, end);
                    }

                    LocalDateTime latestTradeDate = rawQuotes.get(rawQuotes.size() - 1).getTradeDate();
                    List<CorporateAction> effectiveSplits = (splits != null) ? splits.stream()
                            .filter(s -> s != null && s.getEffectiveDate() != null
                                    && (latestTradeDate == null || !s.getEffectiveDate().isAfter(latestTradeDate)))
                            .toList() : Collections.emptyList();

                    List<MarketDailyQuote> adjustedQuotes = FinancialMetricsCalculator.adjustQuotesForSplits(rawQuotes, effectiveSplits);
                    List<MarketDailyQuote> enrichedQuotes = TechnicalIndicatorCalculator.calculateIndicators(adjustedQuotes, currentMonthStart);

                    // Filter quotes strictly belonging to current month
                    List<MarketDailyQuote> currentMonthQuotes = enrichedQuotes.stream()
                            .filter(q -> YearMonth.from(q.getTradeDate()).equals(currentYm))
                            .toList();

                    LocalDateTime latestTradeDateInMonth = currentMonthQuotes.isEmpty() ? null
                            : currentMonthQuotes.get(currentMonthQuotes.size() - 1).getTradeDate();

                    MonthlyQuoteCacheEntry currentMonthEntry = MonthlyQuoteCacheEntry.builder()
                            .ticker(ticker)
                            .yearMonth(currentYm.toString())
                            .latestTradeDate(latestTradeDateInMonth)
                            .quotes(currentMonthQuotes)
                            .build();

                    // CRITICAL: ONLY update current month! Historical months in cache are 100% UNTOUCHED!
                    monthlyQuoteCache.put(buildCacheKey(ticker, currentYm), currentMonthEntry);
                    log.info("[QuoteCache: PATH 2 - Complete] Ticker {}: updated current month {} with {} quotes (latest: {}). Historical months remain untouched.",
                            ticker, currentYm, currentMonthQuotes.size(), latestTradeDateInMonth);

                    return extractAndSlice(ticker, requiredMonths, start, end);
                });
    }

    /**
     * Executes Path 3: Cold Start full reload when historical months are missing.
     * Computes all indicators over a 2-year lookback and populates all monthly chunks.
     */
    private Flux<MarketDailyQuote> executeColdStartFullReload(
            String ticker, LocalDateTime start, LocalDateTime end, List<YearMonth> requiredMonths) {

        LocalDateTime lookbackStart = start.minusYears(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
        log.info("[QuoteCache: PATH 3 - Loading Lookback] Ticker {}: querying lookback window [{} to {}] from DB.",
                ticker, lookbackStart, end);

        Mono<List<MarketDailyQuote>> quotesMono = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, lookbackStart, end)
                .collectList();

        Mono<List<CorporateAction>> splitsMono = (corporateActionRepository != null)
                ? corporateActionRepository.findByTicker(ticker).collectList()
                : Mono.just(Collections.emptyList());

        return Mono.zip(quotesMono, splitsMono)
                .flatMapMany(tuple -> {
                    List<MarketDailyQuote> rawQuotes = tuple.getT1();
                    List<CorporateAction> splits = tuple.getT2();

                    if (rawQuotes.isEmpty()) {
                        for (YearMonth ym : requiredMonths) {
                            monthlyQuoteCache.put(buildCacheKey(ticker, ym), MonthlyQuoteCacheEntry.builder()
                                    .ticker(ticker)
                                    .yearMonth(ym.toString())
                                    .latestTradeDate(null)
                                    .quotes(Collections.emptyList())
                                    .build());
                        }
                        return Flux.empty();
                    }

                    LocalDateTime latestTradeDate = rawQuotes.get(rawQuotes.size() - 1).getTradeDate();
                    List<CorporateAction> effectiveSplits = (splits != null) ? splits.stream()
                            .filter(s -> s != null && s.getEffectiveDate() != null
                                    && (latestTradeDate == null || !s.getEffectiveDate().isAfter(latestTradeDate)))
                            .toList() : Collections.emptyList();

                    LocalDateTime effectiveStart = requiredMonths.get(0).atDay(1).atStartOfDay();
                    List<MarketDailyQuote> adjustedQuotes = FinancialMetricsCalculator.adjustQuotesForSplits(rawQuotes, effectiveSplits);
                    List<MarketDailyQuote> enrichedQuotes = TechnicalIndicatorCalculator.calculateIndicators(adjustedQuotes, effectiveStart);

                    Map<YearMonth, List<MarketDailyQuote>> grouped = enrichedQuotes.stream()
                            .filter(q -> q.getTradeDate() != null && !q.getTradeDate().isBefore(effectiveStart))
                            .collect(Collectors.groupingBy(
                                    q -> YearMonth.from(q.getTradeDate()),
                                    LinkedHashMap::new,
                                    Collectors.toList()
                            ));

                    for (YearMonth ym : requiredMonths) {
                        List<MarketDailyQuote> quotesInMonth = grouped.getOrDefault(ym, Collections.emptyList());
                        LocalDateTime latestTradeDateInMonth = quotesInMonth.isEmpty() ? null
                                : quotesInMonth.get(quotesInMonth.size() - 1).getTradeDate();

                        MonthlyQuoteCacheEntry cacheEntry = MonthlyQuoteCacheEntry.builder()
                                .ticker(ticker)
                                .yearMonth(ym.toString())
                                .latestTradeDate(latestTradeDateInMonth)
                                .quotes(quotesInMonth)
                                .build();

                        monthlyQuoteCache.put(buildCacheKey(ticker, ym), cacheEntry);
                    }

                    log.info("[QuoteCache: PATH 3 - Complete] Ticker {}: initialized {} monthly chunks in cache (skipped warmup lookback).",
                            ticker, requiredMonths.size());

                    return extractAndSlice(ticker, requiredMonths, start, end);
                });
    }

    private Flux<MarketDailyQuote> extractAndSlice(String ticker, List<YearMonth> requiredMonths,
                                                   LocalDateTime start, LocalDateTime end) {
        List<MarketDailyQuote> result = new ArrayList<>();
        for (YearMonth ym : requiredMonths) {
            MonthlyQuoteCacheEntry entry = monthlyQuoteCache.get(buildCacheKey(ticker, ym));
            if (entry != null && entry.getQuotes() != null) {
                for (MarketDailyQuote q : entry.getQuotes()) {
                    if (q.getTradeDate() != null && !q.getTradeDate().isBefore(start) && !q.getTradeDate().isAfter(end)) {
                        result.add(q);
                    }
                }
            }
        }
        result.sort(Comparator.comparing(MarketDailyQuote::getTradeDate));
        return Flux.fromIterable(result);
    }

    public String buildCacheKey(String ticker, YearMonth ym) {
        return ticker + ":" + ym.toString();
    }

    public void evict(String ticker, YearMonth ym) {
        monthlyQuoteCache.remove(buildCacheKey(ticker, ym));
    }

    public void evictTicker(String ticker) {
        if (ticker == null) {
            return;
        }
        List<String> keysToRemove = new ArrayList<>();
        for (Cache.Entry<String, MonthlyQuoteCacheEntry> entry : monthlyQuoteCache) {
            if (entry != null && entry.getKey() != null && entry.getKey().startsWith(ticker + ":")) {
                keysToRemove.add(entry.getKey());
            }
        }
        for (String k : keysToRemove) {
            monthlyQuoteCache.remove(k);
        }
    }

    public void clearCache() {
        monthlyQuoteCache.clear();
    }

    public MonthlyQuoteCacheEntry getEntry(String ticker, YearMonth ym) {
        return monthlyQuoteCache.get(buildCacheKey(ticker, ym));
    }
}
