package com.alphaharvester.domain.math;

import com.alphaharvester.domain.entity.MarketDailyQuote;
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
}
