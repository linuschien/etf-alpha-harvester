package com.alphaharvester.domain.math;

import com.alphaharvester.domain.entity.CorporateAction;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CorporateActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FinancialMetricsCalculatorTest {

    @Test
    @DisplayName("Should correctly calculate daily returns from chronologically unsorted quotes")
    void shouldCalculateDailyReturns() {
        LocalDateTime day1 = LocalDateTime.of(2026, 8, 1, 13, 30);
        LocalDateTime day2 = LocalDateTime.of(2026, 8, 2, 13, 30);
        LocalDateTime day3 = LocalDateTime.of(2026, 8, 3, 13, 30);

        MarketDailyQuote q1 = new MarketDailyQuote(null, null, null, "0050", day1, null, null, null, new BigDecimal("100.00"), null, null, null, null);
        MarketDailyQuote q2 = new MarketDailyQuote(null, null, null, "0050", day2, null, null, null, new BigDecimal("105.00"), null, null, null, null);
        MarketDailyQuote q3 = new MarketDailyQuote(null, null, null, "0050", day3, null, null, null, new BigDecimal("102.90"), null, null, null, null);

        // Pass in reverse order
        Map<LocalDate, Double> returns = FinancialMetricsCalculator.calculateDailyReturns(List.of(q3, q1, q2));

        assertThat(returns).hasSize(2);
        // Day 2 return: (105 - 100) / 100 = 0.05
        assertThat(returns.get(day2.toLocalDate())).isCloseTo(0.05, within(1e-6));
        // Day 3 return: (102.9 - 105) / 105 = -0.02
        assertThat(returns.get(day3.toLocalDate())).isCloseTo(-0.02, within(1e-6));
    }

    @Test
    @DisplayName("Should align return series with same-day and Shift-1 modes")
    void shouldAlignReturnSeries() {
        LocalDate d1 = LocalDate.of(2026, 8, 10);
        LocalDate d2 = LocalDate.of(2026, 8, 11);
        LocalDate d3 = LocalDate.of(2026, 8, 12);

        Map<LocalDate, Double> seriesA = Map.of(d1, 0.01, d2, 0.02, d3, -0.01);
        Map<LocalDate, Double> seriesB = Map.of(d1, 0.015, d2, 0.018, LocalDate.of(2026, 8, 13), 0.005);

        // Same-day alignment (shift = 0)
        FinancialMetricsCalculator.AlignedReturns aligned0 = FinancialMetricsCalculator.alignReturnSeries(seriesA, seriesB, 0);
        assertThat(aligned0.returnsA()).containsExactly(0.01, 0.02);
        assertThat(aligned0.returnsB()).containsExactly(0.015, 0.018);

        // Shift-1 alignment (shift = 1): date T matches latest date in B on or before T - 1
        FinancialMetricsCalculator.AlignedReturns aligned1 = FinancialMetricsCalculator.alignReturnSeries(seriesA, seriesB, 1);
        // For d2 (8/11), target is <= 8/10 -> d1 in B (0.015)
        // For d3 (8/12), target is <= 8/11 -> d2 in B (0.018)
        assertThat(aligned1.returnsA()).hasSize(2);
        assertThat(aligned1.returnsB()).hasSize(2);
    }

    @Test
    @DisplayName("Should calculate Pearson correlation and R^2 with non-negative clamping rule")
    void shouldCalculateCorrelationAndRSquared() {
        // Perfectly positively correlated
        List<Double> retA = List.of(0.01, 0.02, 0.03, -0.01, -0.02);
        List<Double> retB = List.of(0.02, 0.04, 0.06, -0.02, -0.04);

        FinancialMetricsCalculator.CorrelationResult resPos = FinancialMetricsCalculator.calculateCorrelationAndRSquared(retA, retB);
        assertThat(resPos.correlation()).isCloseTo(1.0, within(1e-4));
        assertThat(resPos.rSquared()).isCloseTo(1.0, within(1e-4));

        // Perfectly negatively correlated (rho < 0 -> R^2 must clamp to 0.0)
        List<Double> retNeg = List.of(-0.01, -0.02, -0.03, 0.01, 0.02);
        FinancialMetricsCalculator.CorrelationResult resNeg = FinancialMetricsCalculator.calculateCorrelationAndRSquared(retA, retNeg);
        assertThat(resNeg.correlation()).isCloseTo(-1.0, within(1e-4));
        assertThat(resNeg.rSquared()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate annualized volatility and Sharpe ratio (rf = 0)")
    void shouldCalculateVolatilityAndSharpe() {
        List<Double> returns = List.of(0.01, 0.01, -0.01, 0.02, -0.005);
        double vol = FinancialMetricsCalculator.calculateAnnualizedVolatility(returns);
        double sharpe = FinancialMetricsCalculator.calculateSharpeRatio(returns);

        assertThat(vol).isGreaterThan(0.0);
        assertThat(sharpe).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should calculate Kaufman Efficiency Ratio (KER)")
    void shouldCalculateKaufmanEfficiencyRatio() {
        // Direct straight line upwards: KER should be 1.0
        List<BigDecimal> straightLine = List.of(
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("120"), new BigDecimal("130")
        );
        double ker1 = FinancialMetricsCalculator.calculateKaufmanEfficiencyRatio(straightLine);
        assertThat(ker1).isCloseTo(1.0, within(1e-4));

        // Choppy zigzag path: net change is 10, total path is 10 + 10 + 10 = 30 -> KER = 10 / 30 = 0.3333
        List<BigDecimal> zigzag = List.of(
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("100"), new BigDecimal("110")
        );
        double ker2 = FinancialMetricsCalculator.calculateKaufmanEfficiencyRatio(zigzag);
        assertThat(ker2).isCloseTo(1.0 / 3.0, within(1e-4));
    }

    @Test
    @DisplayName("Should calculate 12-1 momentum correctly")
    void shouldCalculateMomentum12_1() {
        BigDecimal p30d = new BigDecimal("120.00");
        BigDecimal p365d = new BigDecimal("100.00");

        double mom = FinancialMetricsCalculator.calculateMomentum12_1(p30d, p365d);
        assertThat(mom).isCloseTo(0.20, within(1e-4));
    }

    @Test
    @DisplayName("Should calculate continuous percentile ranks with ties handled via average rank")
    void shouldCalculatePercentileRanks() {
        List<String> tickers = List.of("A", "B", "C", "D", "E");
        Map<String, Double> values = Map.of(
                "A", 10.0,
                "B", 20.0,
                "C", 30.0,
                "D", 40.0,
                "E", 50.0
        );

        Map<String, Double> ranks = FinancialMetricsCalculator.calculatePercentileRanks(
                tickers, values::get, true
        );

        // A is lowest -> 0.0, E is highest -> 1.0, C is middle -> 0.5
        assertThat(ranks.get("A")).isCloseTo(0.0, within(1e-4));
        assertThat(ranks.get("C")).isCloseTo(0.5, within(1e-4));
        assertThat(ranks.get("E")).isCloseTo(1.0, within(1e-4));
    }

    @Test
    @DisplayName("Should lookup pairwise R^2 symmetrically by sorting ticker strings")
    void shouldLookupPairwiseRSquaredSymmetrically() {
        // Matrix only stores "0050:006208" where base < target
        Map<String, Double> r2Lookup = Map.of(
                "0050:006208", 0.9850,
                "0050:00757", 0.1200
        );

        // Self-comparison
        assertThat(FinancialMetricsCalculator.getPairwiseRSquared(r2Lookup, "0050", "0050")).isEqualTo(1.0);

        // Direct order ("0050", "006208")
        assertThat(FinancialMetricsCalculator.getPairwiseRSquared(r2Lookup, "0050", "006208")).isCloseTo(0.9850, within(1e-4));

        // Swapped order ("006208", "0050") -> should find identical R^2
        assertThat(FinancialMetricsCalculator.getPairwiseRSquared(r2Lookup, "006208", "0050")).isCloseTo(0.9850, within(1e-4));

        // Missing pair
        assertThat(FinancialMetricsCalculator.getPairwiseRSquared(r2Lookup, "006208", "00757")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should adjust quotes for stock split (1:4 split) and eliminate fake -75% price crash")
    void shouldAdjustQuotesForSplitsCorrectly() {
        LocalDateTime d1 = LocalDateTime.of(2025, 6, 13, 13, 30);
        LocalDateTime d2 = LocalDateTime.of(2025, 6, 14, 13, 30);
        LocalDateTime d3SplitDay = LocalDateTime.of(2025, 6, 15, 9, 0);
        LocalDateTime d4 = LocalDateTime.of(2025, 6, 16, 13, 30);

        MarketDailyQuote q1 = new MarketDailyQuote(null, null, null, "0050", d1,
                new BigDecimal("159.00"), new BigDecimal("161.00"), new BigDecimal("158.50"), new BigDecimal("160.00"),
                1000L, new BigDecimal("160000"), null, null);
        MarketDailyQuote q2 = new MarketDailyQuote(null, null, null, "0050", d2,
                new BigDecimal("160.00"), new BigDecimal("164.00"), new BigDecimal("160.00"), new BigDecimal("164.00"),
                1200L, new BigDecimal("196800"), null, null);
        MarketDailyQuote q3 = new MarketDailyQuote(null, null, null, "0050", d3SplitDay,
                new BigDecimal("41.00"), new BigDecimal("42.00"), new BigDecimal("40.50"), new BigDecimal("41.00"),
                4800L, new BigDecimal("196800"), null, null);
        MarketDailyQuote q4 = new MarketDailyQuote(null, null, null, "0050", d4,
                new BigDecimal("41.00"), new BigDecimal("42.50"), new BigDecimal("41.00"), new BigDecimal("42.00"),
                4500L, new BigDecimal("189000"), null, null);

        CorporateAction split1to4 = new CorporateAction(
                null, null, "0050", CorporateActionType.SPLIT, d3SplitDay, 4, 1
        );

        List<MarketDailyQuote> rawQuotes = List.of(q1, q2, q3, q4);

        // Before adjustment: return between d2 and d3 is (41 - 164) / 164 = -75%
        Map<LocalDate, Double> rawReturns = FinancialMetricsCalculator.calculateDailyReturns(rawQuotes);
        assertThat(rawReturns.get(d3SplitDay.toLocalDate())).isCloseTo(-0.75, within(1e-4));

        // After adjustment
        List<MarketDailyQuote> adjusted = FinancialMetricsCalculator.adjustQuotesForSplits(rawQuotes, List.of(split1to4));
        assertThat(adjusted).hasSize(4);

        // Pre-split quotes adjusted by 1/4 = 0.25
        assertThat(adjusted.get(0).getClosePrice()).isEqualByComparingTo(new BigDecimal("40.0000"));
        assertThat(adjusted.get(1).getClosePrice()).isEqualByComparingTo(new BigDecimal("41.0000"));
        // Post-split quotes remain unchanged
        assertThat(adjusted.get(2).getClosePrice()).isEqualByComparingTo(new BigDecimal("41.00"));
        assertThat(adjusted.get(3).getClosePrice()).isEqualByComparingTo(new BigDecimal("42.00"));

        // Daily returns on adjusted quotes: return on split day is (41 - 41) / 41 = 0.0 (no crash!)
        Map<LocalDate, Double> adjustedReturns = FinancialMetricsCalculator.calculateDailyReturns(adjusted);
        assertThat(adjustedReturns.get(d3SplitDay.toLocalDate())).isCloseTo(0.0, within(1e-4));
        // Return on day 4: (42 - 41) / 41 ~ +2.439%
        assertThat(adjustedReturns.get(d4.toLocalDate())).isCloseTo(0.02439, within(1e-4));
    }

    @Test
    @DisplayName("Should compound multiple splits chronologically")
    void shouldCompoundMultipleSplitsChronologically() {
        LocalDateTime d1 = LocalDateTime.of(2024, 1, 15, 9, 0);
        LocalDateTime splitDate1 = LocalDateTime.of(2024, 6, 1, 9, 0); // 1 to 2
        LocalDateTime d2 = LocalDateTime.of(2024, 8, 15, 9, 0);
        LocalDateTime splitDate2 = LocalDateTime.of(2024, 12, 1, 9, 0); // 1 to 3
        LocalDateTime d3 = LocalDateTime.of(2025, 1, 15, 9, 0);

        MarketDailyQuote q1 = new MarketDailyQuote(null, null, null, "TEST", d1, null, null, null, new BigDecimal("120.00"), null, null, null, null);
        MarketDailyQuote q2 = new MarketDailyQuote(null, null, null, "TEST", d2, null, null, null, new BigDecimal("60.00"), null, null, null, null);
        MarketDailyQuote q3 = new MarketDailyQuote(null, null, null, "TEST", d3, null, null, null, new BigDecimal("25.00"), null, null, null, null);

        CorporateAction split1 = new CorporateAction(null, null, "TEST", CorporateActionType.SPLIT, splitDate1, 2, 1);
        CorporateAction split2 = new CorporateAction(null, null, "TEST", CorporateActionType.SPLIT, splitDate2, 3, 1);

        List<MarketDailyQuote> adjusted = FinancialMetricsCalculator.adjustQuotesForSplits(List.of(q1, q2, q3), List.of(split1, split2));

        // q1 is before split1 and split2 -> factor = (1/2) * (1/3) = 1/6 -> 120 * 1/6 = 20.00
        assertThat(adjusted.get(0).getClosePrice()).isEqualByComparingTo(new BigDecimal("20.0000"));
        // q2 is between split1 and split2 -> factor = 1/3 -> 60 * 1/3 = 20.00
        assertThat(adjusted.get(1).getClosePrice()).isEqualByComparingTo(new BigDecimal("20.0000"));
        // q3 is after split2 -> unchanged = 25.00
        assertThat(adjusted.get(2).getClosePrice()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("Should return 0.0 drawdown when quotes are empty or null")
    void shouldReturnZeroDrawdownWhenQuotesAreEmptyOrNull() {
        assertThat(FinancialMetricsCalculator.calculate52WeekDrawdown(null)).isEqualTo(0.0);
        assertThat(FinancialMetricsCalculator.calculate52WeekDrawdown(Collections.emptyList())).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should correctly calculate 52-week drawdown comparing both high and close prices")
    void shouldCalculate52WeekDrawdownCorrectly() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 13, 30);
        // Current quote: close = 100.0
        MarketDailyQuote current = new MarketDailyQuote(null, null, null, "0050", now,
                null, new BigDecimal("102.0"), null, new BigDecimal("100.0"), null, null, null, null);

        // Peak quote 100 days ago: high = 200.0, close = 195.0
        MarketDailyQuote peak = new MarketDailyQuote(null, null, null, "0050", now.minusDays(100),
                null, new BigDecimal("200.0"), null, new BigDecimal("195.0"), null, null, null, null);

        // Drawdown = (100 - 200) / 200 = -0.50 (-50%)
        double dd = FinancialMetricsCalculator.calculate52WeekDrawdown(List.of(current, peak));
        assertThat(dd).isCloseTo(-0.50, within(1e-4));
    }

    @Test
    @DisplayName("Should respect 1 natural calendar year boundary and exclude older quotes")
    void shouldExcludeQuotesOlderThanOneNaturalYear() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 13, 30);
        MarketDailyQuote current = new MarketDailyQuote(null, null, null, "0050", now,
                null, new BigDecimal("100.0"), null, new BigDecimal("100.0"), null, null, null, null);

        // Peak within 1 year (180 days ago): high = 120.0
        MarketDailyQuote peakRecent = new MarketDailyQuote(null, null, null, "0050", now.minusDays(180),
                null, new BigDecimal("120.0"), null, new BigDecimal("115.0"), null, null, null, null);

        // Peak beyond 1 year (380 days ago): high = 300.0 (must be ignored!)
        MarketDailyQuote peakOld = new MarketDailyQuote(null, null, null, "0050", now.minusDays(380),
                null, new BigDecimal("300.0"), null, new BigDecimal("290.0"), null, null, null, null);

        // Drawdown should be based on 120.0, not 300.0: (100 - 120) / 120 = -16.67%
        double dd = FinancialMetricsCalculator.calculate52WeekDrawdown(List.of(current, peakRecent, peakOld));
        assertThat(dd).isCloseTo(-0.166667, within(1e-4));
    }

    @Test
    @DisplayName("Should handle leap year (366 calendar days) correctly in 52-week drawdown")
    void shouldHandleLeapYearCorrectlyIn52WeekDrawdown() {
        // 2024 is a leap year (366 days between 2023-03-01 and 2024-03-01)
        LocalDateTime leapYearNow = LocalDateTime.of(2024, 3, 1, 13, 30);
        LocalDateTime oneYearAgo = LocalDateTime.of(2023, 3, 1, 13, 30);

        MarketDailyQuote current = new MarketDailyQuote(null, null, null, "0050", leapYearNow,
                null, new BigDecimal("100.0"), null, new BigDecimal("100.0"), null, null, null, null);
        MarketDailyQuote peakLeap = new MarketDailyQuote(null, null, null, "0050", oneYearAgo,
                null, new BigDecimal("200.0"), null, new BigDecimal("190.0"), null, null, null, null);

        // With minusYears(1), 2023-03-01 is included! Drawdown = (100 - 200) / 200 = -50%
        double dd = FinancialMetricsCalculator.calculate52WeekDrawdown(List.of(current, peakLeap));
        assertThat(dd).isCloseTo(-0.50, within(1e-4));
    }
}

