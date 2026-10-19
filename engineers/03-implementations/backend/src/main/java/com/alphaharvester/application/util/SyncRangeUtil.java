package com.alphaharvester.application.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Unified synchronization window and date-range utility across all market data feeds.
 * Caps maximum lookback range at 2 years and enforces standard tiered range definitions.
 */
public final class SyncRangeUtil {

    private SyncRangeUtil() {
    }

    /**
     * Data carrier for resolved synchronization window parameters.
     */
    public record SyncWindow(
            long daysMissed,
            long allowedGap,
            boolean hasGap,
            boolean force,
            int effectiveDays,
            String range,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    /**
     * Resolves the complete synchronization window for a given feed watermark and execution timestamp.
     *
     * @param latestRecordDate previous successful sync record date
     * @param now              current execution timestamp
     * @param backfillDays     user-specified backfill days (force)
     * @param watermarkStatus  status of watermark ("SUCCESS", "FAILED", "HALT", "PENDING")
     * @param isFred           whether this feed is FRED macro yields (T+1 publishing schedule)
     * @return resolved SyncWindow
     */
    public static SyncWindow resolveSyncWindow(
            LocalDateTime latestRecordDate,
            LocalDateTime now,
            Integer backfillDays,
            String watermarkStatus,
            boolean isFred
    ) {
        LocalDateTime effectiveLatest = (latestRecordDate != null) ? latestRecordDate : now.minusDays(1);
        long daysMissed = ChronoUnit.DAYS.between(effectiveLatest.toLocalDate(), now.toLocalDate());

        long allowedGap;
        if (isFred) {
            // FRED T+1 schedule: weekend gap lands on Tuesday (UTC)
            allowedGap = (now.getDayOfWeek() == DayOfWeek.TUESDAY) ? 4 : 2;
        } else {
            // Standard T+0 market close schedule: weekend gap lands on Monday (UTC)
            allowedGap = (now.getDayOfWeek() == DayOfWeek.MONDAY) ? 3 : 1;
        }

        boolean force = backfillDays != null && backfillDays > 0;
        boolean hasGap = daysMissed > allowedGap
                || "HALT".equalsIgnoreCase(watermarkStatus)
                || "FAILED".equalsIgnoreCase(watermarkStatus);

        int effectiveDays;
        if (force) {
            effectiveDays = backfillDays;
        } else if (hasGap) {
            effectiveDays = (int) Math.max(daysMissed, 14);
        } else {
            effectiveDays = 5;
        }

        String range = deriveRange(effectiveDays);
        LocalDate startDate = now.toLocalDate().minusDays(effectiveDays);
        LocalDate endDate = now.toLocalDate();

        return new SyncWindow(daysMissed, allowedGap, hasGap, force, effectiveDays, range, startDate, endDate);
    }

    /**
     * Derives the standardized range string for a given lookback day count.
     * Capped at 2y; 5y removed.
     */
    public static String deriveRange(int days) {
        if (days <= 5) return "5d";
        if (days <= 14) return "14d";
        if (days <= 30) return "1mo";
        if (days <= 90) return "3mo";
        if (days <= 180) return "6mo";
        if (days <= 365) return "1y";
        return "2y";
    }

    /**
     * Derives the corresponding start date for a given standardized range string.
     */
    public static LocalDate deriveStartDate(LocalDate today, String range) {
        return switch (range != null ? range : "2y") {
            case "5d" -> today.minusDays(5);
            case "14d" -> today.minusDays(14);
            case "1mo" -> today.minusMonths(1);
            case "3mo" -> today.minusMonths(3);
            case "6mo" -> today.minusMonths(6);
            case "1y" -> today.minusYears(1);
            default -> today.minusYears(2);
        };
    }
}

