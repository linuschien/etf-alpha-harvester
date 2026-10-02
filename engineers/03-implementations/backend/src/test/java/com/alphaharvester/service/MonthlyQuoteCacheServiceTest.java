package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.service.MonthlyQuoteCacheService;
import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.infrastructure.cache.MonthlyQuoteExpiryPolicy;
import org.ehcache.Cache;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MonthlyQuoteCacheServiceTest {

    @Mock
    private MarketDailyQuoteRepository quoteRepository;

    private PersistentCacheManager cacheManager;
    private Cache<String, MonthlyQuoteCacheEntry> cache;
    private MonthlyQuoteCacheService cacheService;
    private AutoCloseable mocksCloseable;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        mocksCloseable = MockitoAnnotations.openMocks(this);

        File cacheDir = new File(tempDir, "ehcache-test");
        cacheDir.mkdirs();

        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(cacheDir))
                .withCache("monthlyQuoteCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                MonthlyQuoteCacheEntry.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(500, EntryUnit.ENTRIES)
                                        .disk(10, MemoryUnit.MB, true)
                        )
                        .withExpiry(new MonthlyQuoteExpiryPolicy())
                )
                .build(true);

        cache = cacheManager.getCache("monthlyQuoteCache", String.class, MonthlyQuoteCacheEntry.class);
        cacheService = new MonthlyQuoteCacheService(cache, quoteRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cacheManager != null && cacheManager.getStatus() == org.ehcache.Status.AVAILABLE) {
            cacheManager.close();
        }
        if (mocksCloseable != null) {
            mocksCloseable.close();
        }
    }

    @Test
    @DisplayName("Should return empty flux for invalid or inverted date range")
    void shouldReturnEmptyForInvalidDateRange() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 1, 0, 0);

        StepVerifier.create(cacheService.getQuoteTimeSeries("0050", start, end))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should populate cache on miss and calculate technical indicators")
    void shouldPopulateCacheOnMiss() {
        String ticker = "0050";
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 2, 28, 23, 59, 59);

        // Generate 30 quotes in 2026-01 and 20 quotes in 2026-02
        List<MarketDailyQuote> dbQuotes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            MarketDailyQuote q = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                    start.plusDays(i), new BigDecimal("100"), new BigDecimal("105"),
                    new BigDecimal("99"), new BigDecimal("100"), 1000L, new BigDecimal("100000"));
            dbQuotes.add(q);
        }
        for (int i = 0; i < 20; i++) {
            MarketDailyQuote q = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                    LocalDateTime.of(2026, 2, 1, 0, 0).plusDays(i), new BigDecimal("102"), new BigDecimal("106"),
                    new BigDecimal("101"), new BigDecimal("105"), 1000L, new BigDecimal("105000"));
            dbQuotes.add(q);
        }

        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq(ticker), any(), any()))
                .thenReturn(Flux.fromIterable(dbQuotes));

        // First call: Cache Miss
        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(50)
                .verifyComplete();

        // Verify items were cached by month
        MonthlyQuoteCacheEntry entryJan = cacheService.getEntry(ticker, YearMonth.of(2026, 1));
        MonthlyQuoteCacheEntry entryFeb = cacheService.getEntry(ticker, YearMonth.of(2026, 2));

        assertThat(entryJan).isNotNull();
        assertThat(entryJan.getQuotes()).hasSize(30);
        // The 20th quote onwards in Jan should have MA20 populated
        assertThat(entryJan.getQuotes().get(19).getMa20()).isNotNull();

        assertThat(entryFeb).isNotNull();
        assertThat(entryFeb.getQuotes()).hasSize(20);

        // Second call: Cache Hit (Repository should NOT be queried again for lookback)
        reset(quoteRepository);

        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(50)
                .verifyComplete();

        verify(quoteRepository, never()).findByTickerAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any());
    }

    @Test
    @DisplayName("Should perform Lazy Check on ongoing current month and refresh if newer data is present in DB")
    void shouldPerformLazyCheckOnOngoingMonth() {
        String ticker = "0050";
        YearMonth currentYm = YearMonth.now();
        LocalDateTime currentMonthStart = currentYm.atDay(1).atStartOfDay();
        LocalDateTime currentMonthMid = currentYm.atDay(10).atStartOfDay();

        MarketDailyQuote oldQuote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                currentMonthMid, new BigDecimal("100"), new BigDecimal("102"),
                new BigDecimal("99"), new BigDecimal("100"), 1000L, new BigDecimal("100000"));

        // Preload cache with older quote
        MonthlyQuoteCacheEntry initialEntry = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(currentYm.toString())
                .latestTradeDate(currentMonthMid)
                .quotes(List.of(oldQuote))
                .build();
        cache.put(cacheService.buildCacheKey(ticker, currentYm), initialEntry);

        // Newer quote arrives in DB on day 15
        LocalDateTime newerDate = currentYm.atDay(15).atStartOfDay();
        MarketDailyQuote newerQuote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                newerDate, new BigDecimal("102"), new BigDecimal("105"),
                new BigDecimal("101"), new BigDecimal("104"), 1500L, new BigDecimal("156000"));

        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker))
                .thenReturn(Mono.just(newerQuote));

        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq(ticker), any(), any()))
                .thenReturn(Flux.just(oldQuote, newerQuote));

        // Call getQuoteTimeSeries: Lazy check detects newerDate > currentMonthMid => triggers refresh
        LocalDateTime endOfMonth = currentYm.atEndOfMonth().atTime(23, 59, 59);
        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, currentMonthStart, endOfMonth))
                .expectNextCount(2)
                .verifyComplete();

        // Verify cache was updated with the latest date
        MonthlyQuoteCacheEntry updatedEntry = cacheService.getEntry(ticker, currentYm);
        assertThat(updatedEntry.getLatestTradeDate()).isEqualTo(newerDate);
        assertThat(updatedEntry.getQuotes()).hasSize(2);
    }

    @Test
    @DisplayName("Should slice precisely on start and end dates across multiple months")
    void shouldSliceBoundariesPrecisely() {
        String ticker = "0050";
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 2, 10, 23, 59, 59);

        List<MarketDailyQuote> janQuotes = new ArrayList<>();
        for (int day = 1; day <= 31; day++) {
            janQuotes.add(new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                    LocalDateTime.of(2026, 1, day, 12, 0), new BigDecimal("100"), new BigDecimal("101"),
                    new BigDecimal("99"), new BigDecimal("100"), 1000L, new BigDecimal("100000")));
        }
        List<MarketDailyQuote> febQuotes = new ArrayList<>();
        for (int day = 1; day <= 28; day++) {
            febQuotes.add(new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                    LocalDateTime.of(2026, 2, day, 12, 0), new BigDecimal("100"), new BigDecimal("101"),
                    new BigDecimal("99"), new BigDecimal("100"), 1000L, new BigDecimal("100000")));
        }

        // Cache Jan and Feb entries
        cache.put(cacheService.buildCacheKey(ticker, YearMonth.of(2026, 1)),
                new MonthlyQuoteCacheEntry(ticker, "2026-01", LocalDateTime.of(2026, 1, 31, 12, 0), janQuotes));
        cache.put(cacheService.buildCacheKey(ticker, YearMonth.of(2026, 2)),
                new MonthlyQuoteCacheEntry(ticker, "2026-02", LocalDateTime.of(2026, 2, 28, 12, 0), febQuotes));

        // Request jan 15 to feb 10:
        // Jan days 15..31 = 17 days
        // Feb days 1..10 = 10 days
        // Total = 27 days
        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(27)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should verify Ehcache native disk persistence recovers data across CacheManager lifecycle")
    void shouldPersistDataToDiskAcrossRestarts() {
        String ticker = "0050";
        YearMonth ym = YearMonth.now().minusMonths(2);
        String key = cacheService.buildCacheKey(ticker, ym);

        MarketDailyQuote quote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                ym.atDay(15).atStartOfDay(), new BigDecimal("150"), new BigDecimal("152"),
                new BigDecimal("149"), new BigDecimal("151"), 2000L, new BigDecimal("302000"));

        MonthlyQuoteCacheEntry entry = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(ym.toString())
                .latestTradeDate(quote.getTradeDate())
                .quotes(List.of(quote))
                .build();

        cache.put(key, entry);

        // Close cacheManager (flushes persistent disk store)
        File cacheDir = new File(tempDir, "ehcache-test");
        cacheManager.close();

        // Re-open cacheManager pointing to the exact same disk directory
        PersistentCacheManager reopenedManager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(cacheDir))
                .withCache("monthlyQuoteCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                MonthlyQuoteCacheEntry.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(500, EntryUnit.ENTRIES)
                                        .disk(10, MemoryUnit.MB, true)
                        )
                        .withExpiry(new MonthlyQuoteExpiryPolicy())
                )
                .build(true);

        Cache<String, MonthlyQuoteCacheEntry> recoveredCache =
                reopenedManager.getCache("monthlyQuoteCache", String.class, MonthlyQuoteCacheEntry.class);

        MonthlyQuoteCacheEntry recovered = recoveredCache.get(key);
        assertThat(recovered).isNotNull();
        assertThat(recovered.getTicker()).isEqualTo("0050");
        assertThat(recovered.getQuotes()).hasSize(1);
        assertThat(recovered.getQuotes().get(0).getClosePrice()).isEqualByComparingTo("151");

        reopenedManager.close();
    }
}
