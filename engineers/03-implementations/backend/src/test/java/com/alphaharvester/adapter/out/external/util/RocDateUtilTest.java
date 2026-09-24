package com.alphaharvester.adapter.out.external.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RocDateUtilTest {

    @Test
    @DisplayName("Should parse 7-digit ROC date 1150921 to 2026-09-21")
    void shouldParse7DigitRocDate() {
        LocalDateTime fallback = LocalDateTime.now();
        LocalDateTime parsed = RocDateUtil.parseRocDate("1150921", fallback);

        assertThat(parsed.getYear()).isEqualTo(2026);
        assertThat(parsed.getMonthValue()).isEqualTo(9);
        assertThat(parsed.getDayOfMonth()).isEqualTo(21);
    }

    @Test
    @DisplayName("Should parse 6-digit ROC date 0920630 to 2003-06-30")
    void shouldParse6DigitRocDate() {
        LocalDateTime fallback = LocalDateTime.now();
        LocalDateTime parsed = RocDateUtil.parseRocDate("0920630", fallback);

        assertThat(parsed.getYear()).isEqualTo(2003);
        assertThat(parsed.getMonthValue()).isEqualTo(6);
        assertThat(parsed.getDayOfMonth()).isEqualTo(30);
    }

    @Test
    @DisplayName("Should parse ROC date with slashes 115/09/21")
    void shouldParseRocDateWithSlashes() {
        LocalDateTime fallback = LocalDateTime.now();
        LocalDateTime parsed = RocDateUtil.parseRocDate("115/09/21", fallback);

        assertThat(parsed.getYear()).isEqualTo(2026);
        assertThat(parsed.getMonthValue()).isEqualTo(9);
        assertThat(parsed.getDayOfMonth()).isEqualTo(21);
    }

    @Test
    @DisplayName("Should parse 8-digit ISO YYYYMMDD date")
    void shouldParse8DigitIsoDate() {
        LocalDateTime fallback = LocalDateTime.now();
        LocalDateTime parsed = RocDateUtil.parseRocDate("20260921", fallback);

        assertThat(parsed.getYear()).isEqualTo(2026);
        assertThat(parsed.getMonthValue()).isEqualTo(9);
        assertThat(parsed.getDayOfMonth()).isEqualTo(21);
    }

    @Test
    @DisplayName("Should return fallback for invalid or null inputs")
    void shouldReturnFallbackOnInvalidInput() {
        LocalDateTime fallback = LocalDateTime.of(2020, 1, 1, 0, 0);

        assertThat(RocDateUtil.parseRocDate(null, fallback)).isEqualTo(fallback);
        assertThat(RocDateUtil.parseRocDate("", fallback)).isEqualTo(fallback);
        assertThat(RocDateUtil.parseRocDate("invalid", fallback)).isEqualTo(fallback);
    }
}

