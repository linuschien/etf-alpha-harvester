package com.alphaharvester.infrastructure.cache;

import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import org.ehcache.expiry.ExpiryPolicy;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * Dynamic Ehcache ExpiryPolicy implementing the self-expiring TTL formula: (13 - Delta) months.
 *
 * For a cached month chunk {ticker}:{YYYY-MM}:
 * - Delta = months between {YYYY-MM} and current YearMonth.
 * - When Delta >= 13, the month is permanently outside the 1-year visible window and expires immediately (Duration.ZERO).
 * - Otherwise, the entry naturally expires at the start of entryYm + 13 months (UTC).
 * - Native Ehcache self-eviction handles pruning automatically without cron jobs or manual purge code.
 */
public class MonthlyQuoteExpiryPolicy implements ExpiryPolicy<String, MonthlyQuoteCacheEntry> {

    @Override
    public Duration getExpiryForCreation(String key, MonthlyQuoteCacheEntry value) {
        return calculateTtl(key);
    }

    @Override
    public Duration getExpiryForAccess(String key, Supplier<? extends MonthlyQuoteCacheEntry> value) {
        // Return null to keep existing expiry duration unchanged upon cache read
        return null;
    }

    @Override
    public Duration getExpiryForUpdate(String key, Supplier<? extends MonthlyQuoteCacheEntry> oldValue, MonthlyQuoteCacheEntry newValue) {
        // Recalculate expiry when value is updated
        return calculateTtl(key);
    }

    public Duration calculateTtl(String key) {
        if (key == null) {
            return Duration.ofDays(30);
        }
        try {
            String[] parts = key.split(":");
            if (parts.length < 2) {
                return Duration.ofDays(30);
            }
            YearMonth entryYm = YearMonth.parse(parts[1]);
            YearMonth currentYm = YearMonth.now(ZoneOffset.UTC);

            long delta = ChronoUnit.MONTHS.between(entryYm, currentYm);
            if (delta >= 13) {
                return Duration.ZERO;
            }

            Instant expiryInstant = entryYm.plusMonths(13).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant now = Instant.now();

            if (expiryInstant.isBefore(now)) {
                return Duration.ZERO;
            }
            return Duration.between(now, expiryInstant);
        } catch (Exception e) {
            return Duration.ofDays(30);
        }
    }
}
