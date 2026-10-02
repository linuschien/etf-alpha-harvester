package com.alphaharvester.domain.math;

import com.alphaharvester.domain.entity.MarketDailyQuote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalIndicatorCalculatorTest {

    @Test
    @DisplayName("Should return empty list when given null or empty quotes")
    void shouldHandleNullOrEmptyQuotes() {
        assertThat(TechnicalIndicatorCalculator.calculateIndicators(null)).isEmpty();
        assertThat(TechnicalIndicatorCalculator.calculateIndicators(Collections.emptyList())).isEmpty();
    }

    @Test
    @DisplayName("Should correctly calculate MA20 and Bollinger Bands when at least 20 quotes exist")
    void shouldCalculateMa20AndBollingerBands() {
        List<MarketDailyQuote> quotes = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);

        // Create 25 daily quotes with constant close price of 100.00
        for (int i = 0; i < 25; i++) {
            MarketDailyQuote q = new MarketDailyQuote();
            q.setTradeDate(base.plusDays(i));
            q.setClosePrice(new BigDecimal("100.0000"));
            quotes.add(q);
        }

        List<MarketDailyQuote> result = TechnicalIndicatorCalculator.calculateIndicators(quotes);

        assertThat(result).hasSize(25);

        // Quotes index 0..18 (< 20 trading days) should have null MA20 and BB
        for (int i = 0; i < 19; i++) {
            assertThat(result.get(i).getMa20()).isNull();
            assertThat(result.get(i).getBbMiddle()).isNull();
            assertThat(result.get(i).getBbUpper()).isNull();
            assertThat(result.get(i).getBbLower()).isNull();
        }

        // Quote index 19 (the 20th trading day) has MA20 = 100.0000, stdDev = 0, upper = lower = 100.0000
        MarketDailyQuote q19 = result.get(19);
        assertThat(q19.getMa20()).isEqualByComparingTo("100.0000");
        assertThat(q19.getBbMiddle()).isEqualByComparingTo("100.0000");
        assertThat(q19.getBbUpper()).isEqualByComparingTo("100.0000");
        assertThat(q19.getBbLower()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("Should calculate non-zero Bollinger Bands standard deviation accurately")
    void shouldCalculateNonZeroBollingerBands() {
        List<MarketDailyQuote> quotes = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);

        // 10 quotes at 100, 10 quotes at 110 (mean = 105, variance = 25, stdDev = 5)
        for (int i = 0; i < 10; i++) {
            MarketDailyQuote q = new MarketDailyQuote();
            q.setTradeDate(base.plusDays(i));
            q.setClosePrice(new BigDecimal("100.0000"));
            quotes.add(q);
        }
        for (int i = 10; i < 20; i++) {
            MarketDailyQuote q = new MarketDailyQuote();
            q.setTradeDate(base.plusDays(i));
            q.setClosePrice(new BigDecimal("110.0000"));
            quotes.add(q);
        }

        List<MarketDailyQuote> result = TechnicalIndicatorCalculator.calculateIndicators(quotes);
        MarketDailyQuote q19 = result.get(19);

        // Mean = 105.0000
        assertThat(q19.getMa20()).isEqualByComparingTo("105.0000");
        assertThat(q19.getBbMiddle()).isEqualByComparingTo("105.0000");

        // stdDev = 5.0, 2 * stdDev = 10.0
        // upper = 115.0000, lower = 95.0000
        assertThat(q19.getBbUpper()).isEqualByComparingTo("115.0000");
        assertThat(q19.getBbLower()).isEqualByComparingTo("95.0000");
    }

    @Test
    @DisplayName("Should accurately populate MA60, MA120, and MA240 at their respective thresholds")
    void shouldCalculateHigherOrderMovingAverages() {
        List<MarketDailyQuote> quotes = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 0, 0);

        // Generate 250 daily quotes with linearly increasing close price
        for (int i = 0; i < 250; i++) {
            MarketDailyQuote q = new MarketDailyQuote();
            q.setTradeDate(base.plusDays(i));
            q.setClosePrice(BigDecimal.valueOf(i + 1).setScale(4, RoundingMode.HALF_UP));
            quotes.add(q);
        }

        List<MarketDailyQuote> result = TechnicalIndicatorCalculator.calculateIndicators(quotes);

        // Check index 58 (< 60) vs 59 (== 60)
        assertThat(result.get(58).getMa60()).isNull();
        assertThat(result.get(59).getMa60()).isNotNull();
        // Sum of 1..60 = 60 * 61 / 2 = 1830; 1830 / 60 = 30.5
        assertThat(result.get(59).getMa60()).isEqualByComparingTo("30.5000");

        // Check index 118 (< 120) vs 119 (== 120)
        assertThat(result.get(118).getMa120()).isNull();
        assertThat(result.get(119).getMa120()).isNotNull();
        // Sum of 1..120 = 120 * 121 / 2 = 7260; 7260 / 120 = 60.5
        assertThat(result.get(119).getMa120()).isEqualByComparingTo("60.5000");

        // Check index 238 (< 240) vs 239 (== 240)
        assertThat(result.get(238).getMa240()).isNull();
        assertThat(result.get(239).getMa240()).isNotNull();
        // Sum of 1..240 = 240 * 241 / 2 = 28920; 28920 / 240 = 120.5
        assertThat(result.get(239).getMa240()).isEqualByComparingTo("120.5000");
    }
}
