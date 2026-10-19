package com.alphaharvester.application.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRangeUtilTest {

    @Test
    @DisplayName("Should derive correct range string for various day intervals up to 2y and without 5y")
    void shouldDeriveStandardRanges() {
        assertThat(SyncRangeUtil.deriveRange(1)).isEqualTo("5d");
        assertThat(SyncRangeUtil.deriveRange(5)).isEqualTo("5d");
        assertThat(SyncRangeUtil.deriveRange(6)).isEqualTo("14d");
        assertThat(SyncRangeUtil.deriveRange(14)).isEqualTo("14d");
        assertThat(SyncRangeUtil.deriveRange(15)).isEqualTo("1mo");
        assertThat(SyncRangeUtil.deriveRange(30)).isEqualTo("1mo");
        assertThat(SyncRangeUtil.deriveRange(31)).isEqualTo("3mo");
        assertThat(SyncRangeUtil.deriveRange(90)).isEqualTo("3mo");
        assertThat(SyncRangeUtil.deriveRange(91)).isEqualTo("6mo");
        assertThat(SyncRangeUtil.deriveRange(180)).isEqualTo("6mo");
        assertThat(SyncRangeUtil.deriveRange(181)).isEqualTo("1y");
        assertThat(SyncRangeUtil.deriveRange(365)).isEqualTo("1y");
        assertThat(SyncRangeUtil.deriveRange(366)).isEqualTo("2y");
        assertThat(SyncRangeUtil.deriveRange(1000)).isEqualTo("2y");
    }

    @Test
    @DisplayName("Should derive start date corresponding to range string")
    void shouldDeriveStartDate() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        assertThat(SyncRangeUtil.deriveStartDate(today, "5d")).isEqualTo(today.minusDays(5));
        assertThat(SyncRangeUtil.deriveStartDate(today, "14d")).isEqualTo(today.minusDays(14));
        assertThat(SyncRangeUtil.deriveStartDate(today, "1mo")).isEqualTo(today.minusMonths(1));
        assertThat(SyncRangeUtil.deriveStartDate(today, "3mo")).isEqualTo(today.minusMonths(3));
        assertThat(SyncRangeUtil.deriveStartDate(today, "6mo")).isEqualTo(today.minusMonths(6));
        assertThat(SyncRangeUtil.deriveStartDate(today, "1y")).isEqualTo(today.minusYears(1));
        assertThat(SyncRangeUtil.deriveStartDate(today, "2y")).isEqualTo(today.minusYears(2));
        assertThat(SyncRangeUtil.deriveStartDate(today, "unknown")).isEqualTo(today.minusYears(2));
        assertThat(SyncRangeUtil.deriveStartDate(today, null)).isEqualTo(today.minusYears(2));
    }

    @Test
    @DisplayName("Should resolve normal daily sync window with 5d when no gap")
    void shouldResolveNormalDailySyncWindow() {
        // Wednesday UTC, latest record was Monday (2 days missed for FRED, <= 2)
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 22, 0); // Wednesday
        LocalDateTime latest = LocalDateTime.of(2026, 10, 12, 0, 0); // Monday

        SyncRangeUtil.SyncWindow window = SyncRangeUtil.resolveSyncWindow(latest, now, null, "SUCCESS", true);
        assertThat(window.daysMissed()).isEqualTo(2);
        assertThat(window.allowedGap()).isEqualTo(2);
        assertThat(window.hasGap()).isFalse();
        assertThat(window.effectiveDays()).isEqualTo(5);
        assertThat(window.range()).isEqualTo("5d");
        assertThat(window.startDate()).isEqualTo(now.toLocalDate().minusDays(5));
    }

    @Test
    @DisplayName("Should resolve gap window for FRED with Tuesday allowedGap=4")
    void shouldResolveGapWindowForFredOnTuesday() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 22, 0); // Tuesday
        LocalDateTime latest = LocalDateTime.of(2026, 10, 16, 0, 0); // Friday (4 days missed)

        SyncRangeUtil.SyncWindow window = SyncRangeUtil.resolveSyncWindow(latest, now, null, "SUCCESS", true);
        assertThat(window.daysMissed()).isEqualTo(4);
        assertThat(window.allowedGap()).isEqualTo(4);
        assertThat(window.hasGap()).isFalse();
        assertThat(window.effectiveDays()).isEqualTo(5);
        assertThat(window.range()).isEqualTo("5d");
    }

    @Test
    @DisplayName("Should resolve gap window with minimum 14d when gap detected")
    void shouldResolveGapWindowWith14d() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 22, 0); // Sunday
        LocalDateTime latest = LocalDateTime.of(2026, 10, 15, 0, 0); // Thursday (3 days missed > allowedGap 2)

        SyncRangeUtil.SyncWindow window = SyncRangeUtil.resolveSyncWindow(latest, now, null, "SUCCESS", true);
        assertThat(window.daysMissed()).isEqualTo(3);
        assertThat(window.allowedGap()).isEqualTo(2);
        assertThat(window.hasGap()).isTrue();
        assertThat(window.effectiveDays()).isEqualTo(14);
        assertThat(window.range()).isEqualTo("14d");
        assertThat(window.startDate()).isEqualTo(now.toLocalDate().minusDays(14));
    }

    @Test
    @DisplayName("Should resolve force backfill window when backfillDays specified")
    void shouldResolveForceBackfillWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 22, 0);
        LocalDateTime latest = now.minusDays(1);

        SyncRangeUtil.SyncWindow window = SyncRangeUtil.resolveSyncWindow(latest, now, 60, "SUCCESS", false);
        assertThat(window.force()).isTrue();
        assertThat(window.effectiveDays()).isEqualTo(60);
        assertThat(window.range()).isEqualTo("3mo");
        assertThat(window.startDate()).isEqualTo(now.toLocalDate().minusDays(60));
    }
}

