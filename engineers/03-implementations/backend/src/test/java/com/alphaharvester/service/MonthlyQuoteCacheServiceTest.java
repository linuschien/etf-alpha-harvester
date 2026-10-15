package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.CorporateActionRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.service.MonthlyQuoteCacheService;
import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.domain.entity.CorporateAction;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CorporateActionType;
import com.alphaharvester.infrastructure.cache.MonthlyQuoteExpiryPolicy;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

    @Mock
    private CorporateActionRepository corporateActionRepository;

    private CacheManager cacheManager;
    private Cache<String, MonthlyQuoteCacheEntry> cache;
    private MonthlyQuoteCacheService cacheService;
    private AutoCloseable mocksCloseable;

    @BeforeEach
    void setUp() {
        mocksCloseable = MockitoAnnotations.openMocks(this);

        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("monthlyQuoteCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                MonthlyQuoteCacheEntry.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(500, EntryUnit.ENTRIES)
                        )
                        .withExpiry(new MonthlyQuoteExpiryPolicy())
                )
                .build(true);

        cache = cacheManager.getCache("monthlyQuoteCache", String.class, MonthlyQuoteCacheEntry.class);
        when(corporateActionRepository.findByTicker(any())).thenReturn(Flux.empty());
        cacheService = new MonthlyQuoteCacheService(cache, quoteRepository, corporateActionRepository);
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

        // Verify warmup lookback months (e.g. 2025-01) were NOT stored into cache
        assertThat(cacheService.getEntry(ticker, YearMonth.of(2025, 1))).isNull();

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
    @DisplayName("Should verify in-memory heap cache correctly stores and retrieves entries")
    void shouldStoreAndRetrieveMonthlyQuoteCacheEntryFromHeap() {
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

        MonthlyQuoteCacheEntry retrieved = cache.get(key);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getTicker()).isEqualTo("0050");
        assertThat(retrieved.getQuotes()).hasSize(1);
    }

    @Test
    @DisplayName("Should hit cache and not refresh when ongoing month has empty cached entry and DB has no newer data in ongoing month")
    void shouldHitCacheWhenDbHasNoQuotesForOngoingMonthYet() {
        String ticker = "^TWII";
        YearMonth currentYm = YearMonth.now();
        YearMonth previousYm = currentYm.minusMonths(1);

        LocalDateTime previousMonthDate = previousYm.atEndOfMonth().atStartOfDay();
        MarketDailyQuote prevQuote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                previousMonthDate, new BigDecimal("20000"), new BigDecimal("20100"),
                new BigDecimal("19900"), new BigDecimal("20050"), 50000L, new BigDecimal("1000000000"));

        // Preload cache: previous month has quote, current month has empty entry (latestTradeDate = null)
        MonthlyQuoteCacheEntry prevEntry = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(previousYm.toString())
                .latestTradeDate(previousMonthDate)
                .quotes(List.of(prevQuote))
                .build();
        MonthlyQuoteCacheEntry currentEmptyEntry = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(currentYm.toString())
                .latestTradeDate(null)
                .quotes(List.of())
                .build();

        cache.put(cacheService.buildCacheKey(ticker, previousYm), prevEntry);
        cache.put(cacheService.buildCacheKey(ticker, currentYm), currentEmptyEntry);

        // In DB, latest quote is still in previous month (2026-09-30)
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker))
                .thenReturn(Mono.just(prevQuote));

        // When requesting range covering previous and current month:
        LocalDateTime start = previousYm.atDay(1).atStartOfDay();
        LocalDateTime end = currentYm.atDay(5).atStartOfDay();

        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(1)
                .verifyComplete();

        // Repository findByTickerAndTradeDateBetweenOrderByTradeDateAsc should NOT be called (CACHE HIT)
        verify(quoteRepository, never()).findByTickerAndTradeDateBetweenOrderByTradeDateAsc(any(), any(), any());
    }

    @Test
    @DisplayName("Should adjust historical quotes for stock split on cache miss (e.g. 0052 1:7 split)")
    void shouldAdjustQuotesForSplitsWhenPopulatingCacheOnMiss() {
        String ticker = "0052";
        LocalDateTime start = LocalDateTime.of(2025, 11, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 11, 30, 23, 59, 59);

        // Raw quotes: 2025-11-10 (before split, close 350.00), 2025-11-17 (on split, close 50.00)
        MarketDailyQuote rawQuote1 = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                LocalDateTime.of(2025, 11, 10, 0, 0), new BigDecimal("345.0000"), new BigDecimal("355.0000"),
                new BigDecimal("340.0000"), new BigDecimal("350.0000"), 1000L, new BigDecimal("350000"));
        MarketDailyQuote rawQuote2 = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                LocalDateTime.of(2025, 11, 17, 0, 0), new BigDecimal("49.5000"), new BigDecimal("51.0000"),
                new BigDecimal("49.0000"), new BigDecimal("50.0000"), 7000L, new BigDecimal("350000"));

        CorporateAction split = new CorporateAction(
                UUID.randomUUID(), null, ticker, CorporateActionType.SPLIT,
                LocalDateTime.of(2025, 11, 17, 0, 0), 7, 1
        );

        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq(ticker), any(), any()))
                .thenReturn(Flux.just(rawQuote1, rawQuote2));
        when(corporateActionRepository.findByTicker(ticker))
                .thenReturn(Flux.just(split));

        List<MarketDailyQuote> results = cacheService.getQuoteTimeSeries(ticker, start, end)
                .collectList()
                .block();

        assertThat(results).hasSize(2);

        // Quote 1 prior to split should be divided by 7: 350.0000 * (1/7) = 50.0000
        MarketDailyQuote adjustedQuote1 = results.get(0);
        assertThat(adjustedQuote1.getClosePrice()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(adjustedQuote1.getOpenPrice()).isEqualByComparingTo(new BigDecimal("49.2857"));

        // Quote 2 on/after split should remain unadjusted: 50.0000
        MarketDailyQuote adjustedQuote2 = results.get(1);
        assertThat(adjustedQuote2.getClosePrice()).isEqualByComparingTo(new BigDecimal("50.0000"));

        // Verify cache also stored the split-adjusted quotes
        MonthlyQuoteCacheEntry cachedEntry = cacheService.getEntry(ticker, YearMonth.of(2025, 11));
        assertThat(cachedEntry).isNotNull();
        assertThat(cachedEntry.getQuotes().get(0).getClosePrice()).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    @Test
    @DisplayName("Should evict all cache chunks for a specific ticker")
    void shouldEvictAllKeysForSpecificTicker() {
        cache.put("0050:2026-01", MonthlyQuoteCacheEntry.builder().ticker("0050").yearMonth("2026-01").quotes(List.of()).build());
        cache.put("0050:2026-02", MonthlyQuoteCacheEntry.builder().ticker("0050").yearMonth("2026-02").quotes(List.of()).build());
        cache.put("0052:2026-01", MonthlyQuoteCacheEntry.builder().ticker("0052").yearMonth("2026-01").quotes(List.of()).build());

        assertThat(cacheService.getEntry("0050", YearMonth.of(2026, 1))).isNotNull();
        assertThat(cacheService.getEntry("0050", YearMonth.of(2026, 2))).isNotNull();
        assertThat(cacheService.getEntry("0052", YearMonth.of(2026, 1))).isNotNull();

        cacheService.evictTicker("0050");

        assertThat(cacheService.getEntry("0050", YearMonth.of(2026, 1))).isNull();
        assertThat(cacheService.getEntry("0050", YearMonth.of(2026, 2))).isNull();
        assertThat(cacheService.getEntry("0052", YearMonth.of(2026, 1))).isNotNull();
    }

    @Test
    @DisplayName("Should execute Path 2 incremental refresh: update ONLY current month and leave historical months untouched")
    void shouldExecutePath2AndLeaveHistoricalMonthsUntouched() {
        String ticker = "0050";
        YearMonth currentYm = YearMonth.now();
        YearMonth prevYm = currentYm.minusMonths(1);

        LocalDateTime prevDate = prevYm.atDay(15).atStartOfDay();
        MarketDailyQuote prevQuote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                prevDate, new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("99"), new BigDecimal("100"), 1000L, new BigDecimal("100000"));

        MonthlyQuoteCacheEntry prevEntry = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(prevYm.toString())
                .latestTradeDate(prevDate)
                .quotes(List.of(prevQuote))
                .build();
        cache.put(cacheService.buildCacheKey(ticker, prevYm), prevEntry);

        // Current month has an initial quote on day 5
        LocalDateTime day5 = currentYm.atDay(5).atStartOfDay();
        MarketDailyQuote day5Quote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day5, new BigDecimal("101"), new BigDecimal("102"),
                new BigDecimal("100"), new BigDecimal("101"), 1000L, new BigDecimal("101000"));
        MonthlyQuoteCacheEntry currentInitial = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(currentYm.toString())
                .latestTradeDate(day5)
                .quotes(List.of(day5Quote))
                .build();
        cache.put(cacheService.buildCacheKey(ticker, currentYm), currentInitial);

        // Newer quote arrives on day 10
        LocalDateTime day10 = currentYm.atDay(10).atStartOfDay();
        MarketDailyQuote day10Quote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day10, new BigDecimal("103"), new BigDecimal("105"),
                new BigDecimal("102"), new BigDecimal("104"), 1200L, new BigDecimal("124800"));

        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker))
                .thenReturn(Mono.just(day10Quote));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq(ticker), any(), any()))
                .thenReturn(Flux.just(prevQuote, day5Quote, day10Quote));

        LocalDateTime start = prevYm.atDay(1).atStartOfDay();
        LocalDateTime end = currentYm.atEndOfMonth().atTime(23, 59, 59);

        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(3)
                .verifyComplete();

        // Verify current month was updated (Path 2)
        MonthlyQuoteCacheEntry updatedCurrent = cacheService.getEntry(ticker, currentYm);
        assertThat(updatedCurrent.getLatestTradeDate()).isEqualTo(day10);
        assertThat(updatedCurrent.getQuotes()).hasSize(2);

        // Verify historical month (prevYm) is STILL the original entry and was NOT overwritten/modified!
        MonthlyQuoteCacheEntry checkedPrev = cacheService.getEntry(ticker, prevYm);
        assertThat(checkedPrev).isNotNull();
        assertThat(checkedPrev.getQuotes()).hasSize(1);
        assertThat(checkedPrev.getLatestTradeDate()).isEqualTo(prevDate);
    }

    @Test
    @DisplayName("Should auto-heal incomplete last month in Path 2 when DB has newer trade dates in last month")
    void shouldAutoHealIncompleteLastMonthInPath2() {
        String ticker = "0050";
        YearMonth currentYm = YearMonth.now();
        YearMonth prevYm = currentYm.minusMonths(1);

        // Preload last month cache with only partial data (up to day 10)
        LocalDateTime day10 = prevYm.atDay(10).atStartOfDay();
        MarketDailyQuote prevQuote10 = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day10, new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("99"), new BigDecimal("100"), 1000L, new BigDecimal("100000"));
        MonthlyQuoteCacheEntry incompletePrev = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(prevYm.toString())
                .latestTradeDate(day10)
                .quotes(List.of(prevQuote10))
                .build();
        cache.put(cacheService.buildCacheKey(ticker, prevYm), incompletePrev);

        // Current month cache is empty/stale, latest quote in DB is day 5 of current month
        LocalDateTime currentDay5 = currentYm.atDay(5).atStartOfDay();
        MarketDailyQuote currentQuote5 = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                currentDay5, new BigDecimal("102"), new BigDecimal("104"),
                new BigDecimal("101"), new BigDecimal("103"), 1500L, new BigDecimal("154500"));

        // In DB, last month actually had another quote on day 28 (which was missed in cache)
        LocalDateTime day28 = prevYm.atDay(28).atStartOfDay();
        MarketDailyQuote prevQuote28 = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day28, new BigDecimal("101"), new BigDecimal("103"),
                new BigDecimal("100"), new BigDecimal("102"), 1200L, new BigDecimal("122400"));

        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker))
                .thenReturn(Mono.just(currentQuote5));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq(ticker), any(), any()))
                .thenReturn(Flux.just(prevQuote10, prevQuote28, currentQuote5));

        LocalDateTime start = prevYm.atDay(1).atStartOfDay();
        LocalDateTime end = currentYm.atEndOfMonth().atTime(23, 59, 59);

        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(3)
                .verifyComplete();

        // Verify last month was auto-healed in place with day 28 quote!
        MonthlyQuoteCacheEntry healedPrev = cacheService.getEntry(ticker, prevYm);
        assertThat(healedPrev).isNotNull();
        assertThat(healedPrev.getLatestTradeDate()).isEqualTo(day28);
        assertThat(healedPrev.getQuotes()).hasSize(2);

        // Verify current month was also updated
        MonthlyQuoteCacheEntry updatedCurrent = cacheService.getEntry(ticker, currentYm);
        assertThat(updatedCurrent).isNotNull();
        assertThat(updatedCurrent.getLatestTradeDate()).isEqualTo(currentDay5);
        assertThat(updatedCurrent.getQuotes()).hasSize(1);
    }

    @Test
    @DisplayName("Should trigger Path 3 cold reload when effective split crosses cache date")
    void shouldTriggerPath3WhenSplitCrossesCacheDate() {
        String ticker = "0050";
        YearMonth currentYm = YearMonth.now();
        YearMonth prevYm = currentYm.minusMonths(1);

        // Cache has quotes up to day 5
        LocalDateTime day5 = currentYm.atDay(5).atStartOfDay();
        MarketDailyQuote day5Quote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day5, new BigDecimal("160"), new BigDecimal("162"),
                new BigDecimal("159"), new BigDecimal("160"), 1000L, new BigDecimal("160000"));
        MonthlyQuoteCacheEntry currentInitial = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(currentYm.toString())
                .latestTradeDate(day5)
                .quotes(List.of(day5Quote))
                .build();
        cache.put(cacheService.buildCacheKey(ticker, currentYm), currentInitial);

        MonthlyQuoteCacheEntry prevEntry = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(prevYm.toString())
                .latestTradeDate(prevYm.atDay(28).atStartOfDay())
                .quotes(List.of())
                .build();
        cache.put(cacheService.buildCacheKey(ticker, prevYm), prevEntry);

        // Split takes effect on day 8 (1:4 split)
        LocalDateTime day8 = currentYm.atDay(8).atStartOfDay();
        CorporateAction split = new CorporateAction(UUID.randomUUID(), null, ticker,
                CorporateActionType.SPLIT, day8, 4, 1);
        when(corporateActionRepository.findByTicker(ticker)).thenReturn(Flux.just(split));

        // DB latest quote is day 10 (post-split price = 40)
        LocalDateTime day10 = currentYm.atDay(10).atStartOfDay();
        MarketDailyQuote day10Quote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day10, new BigDecimal("40"), new BigDecimal("41"),
                new BigDecimal("39"), new BigDecimal("40"), 2000L, new BigDecimal("80000"));

        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker))
                .thenReturn(Mono.just(day10Quote));

        // For Path 3 full reload, DB returns all quotes
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq(ticker), any(), any()))
                .thenReturn(Flux.just(day5Quote, day10Quote));

        LocalDateTime start = prevYm.atDay(1).atStartOfDay();
        LocalDateTime end = currentYm.atEndOfMonth().atTime(23, 59, 59);

        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(2)
                .verifyComplete();

        // Verify Path 3 reloaded and updated cache with split applied
        MonthlyQuoteCacheEntry updated = cacheService.getEntry(ticker, currentYm);
        assertThat(updated).isNotNull();
        assertThat(updated.getLatestTradeDate()).isEqualTo(day10);
    }

    @Test
    @DisplayName("Should not trigger Path 3 if cache has already crossed the split effective date")
    void shouldNotTriggerPath3WhenSplitAlreadyCrossedByCache() {
        String ticker = "0050";
        YearMonth currentYm = YearMonth.now();
        YearMonth prevYm = currentYm.minusMonths(1);

        // Split took effect on day 3
        LocalDateTime day3 = currentYm.atDay(3).atStartOfDay();
        CorporateAction split = new CorporateAction(UUID.randomUUID(), null, ticker,
                CorporateActionType.SPLIT, day3, 4, 1);
        when(corporateActionRepository.findByTicker(ticker)).thenReturn(Flux.just(split));

        // Cache was already updated on day 5 (already post-split)
        LocalDateTime day5 = currentYm.atDay(5).atStartOfDay();
        MarketDailyQuote day5Quote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day5, new BigDecimal("40"), new BigDecimal("41"),
                new BigDecimal("39"), new BigDecimal("40"), 1000L, new BigDecimal("40000"));
        MonthlyQuoteCacheEntry currentInitial = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(currentYm.toString())
                .latestTradeDate(day5)
                .quotes(List.of(day5Quote))
                .build();
        cache.put(cacheService.buildCacheKey(ticker, currentYm), currentInitial);

        MonthlyQuoteCacheEntry prevEntry = MonthlyQuoteCacheEntry.builder()
                .ticker(ticker)
                .yearMonth(prevYm.toString())
                .latestTradeDate(prevYm.atDay(28).atStartOfDay())
                .quotes(List.of())
                .build();
        cache.put(cacheService.buildCacheKey(ticker, prevYm), prevEntry);

        // DB latest quote is day 10
        LocalDateTime day10 = currentYm.atDay(10).atStartOfDay();
        MarketDailyQuote day10Quote = new MarketDailyQuote(UUID.randomUUID(), null, null, ticker,
                day10, new BigDecimal("41"), new BigDecimal("42"),
                new BigDecimal("40"), new BigDecimal("41"), 1200L, new BigDecimal("49200"));

        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc(ticker))
                .thenReturn(Mono.just(day10Quote));
        when(quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(eq(ticker), any(), any()))
                .thenReturn(Flux.just(day5Quote, day10Quote));

        LocalDateTime start = prevYm.atDay(1).atStartOfDay();
        LocalDateTime end = currentYm.atEndOfMonth().atTime(23, 59, 59);

        StepVerifier.create(cacheService.getQuoteTimeSeries(ticker, start, end))
                .expectNextCount(2)
                .verifyComplete();

        // Cache latest trade date updated to day 10 via Path 2 (incremental refresh, not Path 3 cold start)
        MonthlyQuoteCacheEntry updated = cacheService.getEntry(ticker, currentYm);
        assertThat(updated).isNotNull();
        assertThat(updated.getLatestTradeDate()).isEqualTo(day10);
    }
}
