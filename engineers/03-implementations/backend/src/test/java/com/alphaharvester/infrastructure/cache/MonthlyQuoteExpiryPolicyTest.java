package com.alphaharvester.infrastructure.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyQuoteExpiryPolicyTest {

    private final MonthlyQuoteExpiryPolicy policy = new MonthlyQuoteExpiryPolicy();

    @Test
    @DisplayName("Should return default duration when key is null or malformed")
    void shouldHandleNullOrMalformedKey() {
        assertThat(policy.calculateTtl(null)).isEqualTo(Duration.ofDays(30));
        assertThat(policy.calculateTtl("invalid-key")).isEqualTo(Duration.ofDays(30));
    }

    @Test
    @DisplayName("Should calculate natural expiration for ongoing current month (Delta = 0)")
    void shouldCalculateTtlForCurrentMonth() {
        YearMonth currentYm = YearMonth.now(ZoneOffset.UTC);
        String key = "0050:" + currentYm;

        Duration ttl = policy.calculateTtl(key);

        // Delta = 0 => entryYm.plusMonths(13) => approximately 13 months (~365 - 400 days)
        assertThat(ttl.toDays()).isBetween(360L, 410L);
    }

    @Test
    @DisplayName("Should calculate natural expiration for 1 month ago (Delta = 1)")
    void shouldCalculateTtlForOneMonthAgo() {
        YearMonth oneMonthAgo = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        String key = "0050:" + oneMonthAgo;

        Duration ttl = policy.calculateTtl(key);

        // Delta = 1 => 13 - 1 = 12 months remaining (~330 - 380 days)
        assertThat(ttl.toDays()).isBetween(330L, 380L);
    }

    @Test
    @DisplayName("Should calculate natural expiration for 12 months ago (Delta = 12)")
    void shouldCalculateTtlForTwelveMonthsAgo() {
        YearMonth twelveMonthsAgo = YearMonth.now(ZoneOffset.UTC).minusMonths(12);
        String key = "0050:" + twelveMonthsAgo;

        Duration ttl = policy.calculateTtl(key);

        // Delta = 12 => 13 - 12 = 1 month remaining (> 0 days, <= 32 days)
        assertThat(ttl).isGreaterThan(Duration.ZERO);
        assertThat(ttl.toDays()).isLessThanOrEqualTo(32L);
    }

    @Test
    @DisplayName("Should return Duration.ZERO for Delta >= 13 months (permanently outside visible window)")
    void shouldExpireImmediatelyForThirteenMonthsOrOlder() {
        YearMonth thirteenMonthsAgo = YearMonth.now(ZoneOffset.UTC).minusMonths(13);
        String key13 = "0050:" + thirteenMonthsAgo;
        assertThat(policy.calculateTtl(key13)).isEqualTo(Duration.ZERO);

        YearMonth twentyMonthsAgo = YearMonth.now(ZoneOffset.UTC).minusMonths(20);
        String key20 = "0050:" + twentyMonthsAgo;
        assertThat(policy.calculateTtl(key20)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Should verify getExpiryForAccess returns null and getExpiryForUpdate recalculates")
    void shouldVerifyLifecycleMethods() {
        String key = "0050:2026-01";
        assertThat(policy.getExpiryForAccess(key, () -> null)).isNull();
        assertThat(policy.getExpiryForCreation(key, null)).isNotNull();
        assertThat(policy.getExpiryForUpdate(key, () -> null, null)).isNotNull();
    }
}
