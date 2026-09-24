package com.alphaharvester.adapter.out.external.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RocDateUtil {

    private RocDateUtil() {
    }

    /**
     * Converts ROC date format to LocalDateTime at start of day, returning null if unparseable.
     */
    public static LocalDateTime parseRocDate(String rocStr) {
        return parseRocDate(rocStr, null);
    }

    /**
     * Converts ROC date format (e.g. '1150921', '0920630', '115/09/21', '115-09-21') to LocalDateTime at start of day.
     * If date string is empty or cannot be parsed, returns fallback.
     */
    public static LocalDateTime parseRocDate(String rocStr, LocalDateTime fallback) {
        if (rocStr == null || rocStr.isBlank()) {
            return fallback;
        }
        String clean = rocStr.trim().replace("/", "").replace("-", "");
        try {
            int rocYear;
            int month;
            int day;
            if (clean.length() == 7) {
                rocYear = Integer.parseInt(clean.substring(0, 3));
                month = Integer.parseInt(clean.substring(3, 5));
                day = Integer.parseInt(clean.substring(5, 7));
            } else if (clean.length() == 6) {
                rocYear = Integer.parseInt(clean.substring(0, 2));
                month = Integer.parseInt(clean.substring(2, 4));
                day = Integer.parseInt(clean.substring(4, 6));
            } else if (clean.length() == 8) {
                // ISO YYYYMMDD
                return LocalDate.parse(clean, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
            } else {
                return fallback;
            }
            int adYear = rocYear + 1911;
            return LocalDate.of(adYear, month, day).atStartOfDay();
        } catch (Exception e) {
            return fallback;
        }
    }
}

