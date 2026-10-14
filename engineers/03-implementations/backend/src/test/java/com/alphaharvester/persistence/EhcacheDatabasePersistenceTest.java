package com.alphaharvester.persistence;

import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import org.ehcache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.SerializationUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EhcacheDatabasePersistenceTest {

    @Autowired
    private Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache;

    @Autowired
    private JdbcTemplate cacheJdbcTemplate;

    private static final String TEST_KEY = "PERSIST_TEST:2026-09";

    @BeforeEach
    @AfterEach
    void cleanUp() {
        monthlyQuoteCache.remove(TEST_KEY);
        cacheJdbcTemplate.update("DELETE FROM monthly_quote_cache WHERE cache_key = ?", TEST_KEY);
    }

    @Test
    @DisplayName("Should execute 3-step contract: 1. Put writes to DB, 2. Get after memory clear triggers Read-Through, 3. Remove deletes from DB")
    void shouldExecuteThreeStepCachePersistenceContract() throws Exception {
        // Given a sample monthly quote cache entry
        MarketDailyQuote sampleQuote = new MarketDailyQuote();
        sampleQuote.setId(UUID.randomUUID());
        sampleQuote.setTicker("PERSIST_TEST");
        sampleQuote.setTradeDate(LocalDateTime.of(2026, 9, 30, 13, 30));
        sampleQuote.setClosePrice(BigDecimal.valueOf(185.50));

        MonthlyQuoteCacheEntry testEntry = MonthlyQuoteCacheEntry.builder()
                .ticker("PERSIST_TEST")
                .yearMonth("2026-09")
                .latestTradeDate(sampleQuote.getTradeDate())
                .quotes(List.of(sampleQuote))
                .build();

        // ── STEP 1: Put into cache and verify Write-Behind asynchronously writes to DB ──
        monthlyQuoteCache.put(TEST_KEY, testEntry);

        // Await background Write-Behind worker to persist to H2
        awaitCondition(() -> {
            Integer count = cacheJdbcTemplate.queryForObject(
                    "SELECT count(*) FROM monthly_quote_cache WHERE cache_key = ?",
                    Integer.class,
                    TEST_KEY
            );
            return count != null && count == 1;
        }, 3000);

        // Directly query DB table to verify binary payload
        byte[] dbPayload = cacheJdbcTemplate.queryForObject(
                "SELECT payload_data FROM monthly_quote_cache WHERE cache_key = ?",
                byte[].class,
                TEST_KEY
        );
        assertThat(dbPayload).isNotNull();

        MonthlyQuoteCacheEntry deserializedFromDb = (MonthlyQuoteCacheEntry) SerializationUtils.deserialize(dbPayload);
        assertThat(deserializedFromDb).isNotNull();
        assertThat(deserializedFromDb.getTicker()).isEqualTo("PERSIST_TEST");
        assertThat(deserializedFromDb.getYearMonth()).isEqualTo("2026-09");
        assertThat(deserializedFromDb.getQuotes()).hasSize(1);
        assertThat(deserializedFromDb.getQuotes().get(0).getClosePrice()).isEqualByComparingTo(BigDecimal.valueOf(185.50));

        // ── STEP 2: Clear in-memory cache and verify Read-Through reloads from DB ──
        monthlyQuoteCache.clear();

        // Calling get() should trigger CacheLoaderWriter.load() from DB
        MonthlyQuoteCacheEntry loadedEntry = monthlyQuoteCache.get(TEST_KEY);
        assertThat(loadedEntry).isNotNull();
        assertThat(loadedEntry.getTicker()).isEqualTo("PERSIST_TEST");
        assertThat(loadedEntry.getYearMonth()).isEqualTo("2026-09");
        assertThat(loadedEntry.getQuotes()).hasSize(1);
        assertThat(loadedEntry.getQuotes().get(0).getClosePrice()).isEqualByComparingTo(BigDecimal.valueOf(185.50));

        // ── STEP 3: Remove from cache and verify Write-Behind deletes from DB ──
        monthlyQuoteCache.remove(TEST_KEY);

        // Await background Write-Behind worker to delete from H2
        awaitCondition(() -> {
            Integer count = cacheJdbcTemplate.queryForObject(
                    "SELECT count(*) FROM monthly_quote_cache WHERE cache_key = ?",
                    Integer.class,
                    TEST_KEY
            );
            return count != null && count == 0;
        }, 3000);

        // Verify row is completely deleted in DB
        Integer finalCount = cacheJdbcTemplate.queryForObject(
                "SELECT count(*) FROM monthly_quote_cache WHERE cache_key = ?",
                Integer.class,
                TEST_KEY
        );
        assertThat(finalCount).isZero();
    }

    private void awaitCondition(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}

