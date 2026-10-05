package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.domain.entity.MarketDailyQuote;
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
 * Service managing monthly cached technical indicator quotes backed by Ehcache with disk persistence.
 *
 * Implements:
 * 1. Monthly chunk key format: "{ticker}:{YYYY-MM}"
 * 2. 2-year DB lookback depth for accurate MA20/60/120/240 and Bollinger Bands calculation
 * 3. Lazy check for the ongoing calendar month against the latest trade date in DB
 * 4. Head and tail date boundary slicing across multi-month windows
 */
@Service
public class MonthlyQuoteCacheService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyQuoteCacheService.class);

    private final Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache;
    private final MarketDailyQuoteRepository quoteRepository;

    public MonthlyQuoteCacheService(Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache,
                                    MarketDailyQuoteRepository quoteRepository) {
        this.monthlyQuoteCache = monthlyQuoteCache;
        this.quoteRepository = quoteRepository;
    }

    /**
     * Retrieves enriched daily quotes (with MA20/60/120/240 and BB) for a specific date range.
     * Reuses monthly cached chunks whenever available.
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

        // Check cache for all required months
        boolean hasMiss = false;
        for (YearMonth ym : requiredMonths) {
            String key = buildCacheKey(ticker, ym);
            MonthlyQuoteCacheEntry entry = monthlyQuoteCache.get(key);
            if (entry == null) {
                hasMiss = true;
                break;
            }
        }

        // Lazy check for current ongoing month if present in range
        Mono<Boolean> needsRefreshMono;
        if (hasMiss) {
            needsRefreshMono = Mono.just(true);
        } else if (requiredMonths.contains(currentYm)) {
            String currentMonthKey = buildCacheKey(ticker, currentYm);
            MonthlyQuoteCacheEntry currentEntry = monthlyQuoteCache.get(currentMonthKey);
            needsRefreshMono = quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker)
                    .map(latestDbQuote -> {
                        if (currentEntry == null) {
                            return true;
                        }
                        if (latestDbQuote == null || latestDbQuote.getTradeDate() == null) {
                            return false;
                        }
                        YearMonth latestDbYm = YearMonth.from(latestDbQuote.getTradeDate());
                        if (latestDbYm.isBefore(currentYm)) {
                            // DB has no records for current ongoing month yet; cache is up-to-date
                            return false;
                        }
                        if (currentEntry.getLatestTradeDate() == null) {
                            // DB now has records in ongoing month, but cache had none
                            return true;
                        }
                        return latestDbQuote.getTradeDate().isAfter(currentEntry.getLatestTradeDate());
                    })
                    .defaultIfEmpty(false);
        } else {
            needsRefreshMono = Mono.just(false);
        }

        return needsRefreshMono.flatMapMany(needsRefresh -> {
            if (needsRefresh) {
                // Fetch up to 2-year lookback from DB (start minus 1 year lookback to guarantee 240 trading days)
                LocalDateTime lookbackStart = start.minusYears(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
                log.debug("Cache miss/refresh for ticker {}: loading lookback data from {} to {}", ticker, lookbackStart, end);

                return quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, lookbackStart, end)
                        .collectList()
                        .flatMapMany(rawQuotes -> {
                            if (rawQuotes.isEmpty()) {
                                // Cache empty entries for required months to avoid repeated DB misses
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

                            // Compute technical indicators on the full lookback series
                            List<MarketDailyQuote> enrichedQuotes = TechnicalIndicatorCalculator.calculateIndicators(rawQuotes);

                            // Group quotes by YearMonth
                            Map<YearMonth, List<MarketDailyQuote>> grouped = enrichedQuotes.stream()
                                    .collect(Collectors.groupingBy(
                                            q -> YearMonth.from(q.getTradeDate()),
                                            LinkedHashMap::new,
                                            Collectors.toList()
                                    ));

                            // Populate cache for all months in lookback series
                            for (Map.Entry<YearMonth, List<MarketDailyQuote>> entry : grouped.entrySet()) {
                                YearMonth ym = entry.getKey();
                                List<MarketDailyQuote> quotesInMonth = entry.getValue();
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

                            // Also ensure required months with zero quotes are cached
                            for (YearMonth ym : requiredMonths) {
                                if (!grouped.containsKey(ym)) {
                                    monthlyQuoteCache.put(buildCacheKey(ticker, ym), MonthlyQuoteCacheEntry.builder()
                                            .ticker(ticker)
                                            .yearMonth(ym.toString())
                                            .latestTradeDate(null)
                                            .quotes(Collections.emptyList())
                                            .build());
                                }
                            }

                            return extractAndSlice(ticker, requiredMonths, start, end);
                        });
            } else {
                log.debug("Cache hit for ticker {} across {} months", ticker, requiredMonths.size());
                return extractAndSlice(ticker, requiredMonths, start, end);
            }
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

    public void clearCache() {
        monthlyQuoteCache.clear();
    }

    public MonthlyQuoteCacheEntry getEntry(String ticker, YearMonth ym) {
        return monthlyQuoteCache.get(buildCacheKey(ticker, ym));
    }
}
