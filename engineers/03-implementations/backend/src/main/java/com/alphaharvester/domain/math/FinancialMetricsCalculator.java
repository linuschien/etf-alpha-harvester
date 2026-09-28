package com.alphaharvester.domain.math;

import com.alphaharvester.domain.entity.MarketDailyQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Reusable Financial and Mathematical Engine for quantitative indicator and multi-factor evaluation.
 * Pure, stateless functions operating on objective market data (Raw Close).
 */
public final class FinancialMetricsCalculator {

    private FinancialMetricsCalculator() {
        // Utility class
    }

    public record AlignedReturns(List<Double> returnsA, List<Double> returnsB) {}
    public record CorrelationResult(double correlation, double rSquared) {}

    /**
     * Calculates daily percentage returns from a sequence of daily quotes.
     * Quotes are automatically sorted chronologically ascending by trade date.
     * Returns a map of (Trade Date -> Daily Return r_t = (P_t - P_{t-1}) / P_{t-1}).
     */
    public static Map<LocalDate, Double> calculateDailyReturns(List<MarketDailyQuote> quotes) {
        if (quotes == null || quotes.size() < 2) {
            return Collections.emptyMap();
        }

        // Sort chronologically ascending
        List<MarketDailyQuote> sorted = quotes.stream()
                .filter(q -> q != null && q.getTradeDate() != null && q.getClosePrice() != null && q.getClosePrice().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(MarketDailyQuote::getTradeDate))
                .toList();

        Map<LocalDate, Double> returnMap = new TreeMap<>();
        for (int i = 1; i < sorted.size(); i++) {
            MarketDailyQuote prev = sorted.get(i - 1);
            MarketDailyQuote curr = sorted.get(i);

            double pPrev = prev.getClosePrice().doubleValue();
            double pCurr = curr.getClosePrice().doubleValue();
            if (pPrev > 0.0) {
                double r = (pCurr - pPrev) / pPrev;
                returnMap.put(curr.getTradeDate().toLocalDate(), r);
            }
        }
        return returnMap;
    }

    /**
     * Calculates daily percentage returns from a list of raw close prices.
     */
    public static List<Double> calculateDailyReturnsFromPrices(List<BigDecimal> prices) {
        if (prices == null || prices.size() < 2) {
            return Collections.emptyList();
        }
        List<Double> returns = new ArrayList<>(prices.size() - 1);
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal prev = prices.get(i - 1);
            BigDecimal curr = prices.get(i);
            if (prev != null && curr != null && prev.compareTo(BigDecimal.ZERO) > 0) {
                double r = curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP).doubleValue();
                returns.add(r);
            }
        }
        return returns;
    }

    /**
     * Aligns two daily return series on common dates.
     * If shiftDaysB == 0: exact same-day alignment (e.g. Taiwan ETF vs Taiwan TAIEX or Nikkei 225).
     * If shiftDaysB == 1: US market shift-1 alignment (Taiwan trade date T aligns with US trade date T-1).
     */
    public static AlignedReturns alignReturnSeries(Map<LocalDate, Double> seriesA,
                                                   Map<LocalDate, Double> seriesB,
                                                   int shiftDaysB) {
        if (seriesA == null || seriesB == null || seriesA.isEmpty() || seriesB.isEmpty()) {
            return new AlignedReturns(Collections.emptyList(), Collections.emptyList());
        }

        List<Double> retA = new ArrayList<>();
        List<Double> retB = new ArrayList<>();

        if (shiftDaysB == 0) {
            // Same-day intersection
            for (Map.Entry<LocalDate, Double> entry : seriesA.entrySet()) {
                LocalDate date = entry.getKey();
                if (seriesB.containsKey(date)) {
                    retA.add(entry.getValue());
                    retB.add(seriesB.get(date));
                }
            }
        } else {
            // Shift-1 alignment: for date T in A, match with latest date in B on or before T - 1 day
            TreeMap<LocalDate, Double> sortedB = new TreeMap<>(seriesB);
            for (Map.Entry<LocalDate, Double> entry : seriesA.entrySet()) {
                LocalDate dateA = entry.getKey();
                LocalDate targetBDate = dateA.minusDays(shiftDaysB);
                // Floor date in B to handle US holidays/weekends
                LocalDate floorDateB = sortedB.floorKey(targetBDate);
                if (floorDateB != null && !floorDateB.isBefore(dateA.minusDays(shiftDaysB + 4))) {
                    retA.add(entry.getValue());
                    retB.add(sortedB.get(floorDateB));
                }
            }
        }

        return new AlignedReturns(retA, retB);
    }

    /**
     * Calculates Pearson correlation coefficient (rho) and determination coefficient (R^2).
     * Rule: if rho <= 0, R^2 is clamped to 0.0.
     */
    public static CorrelationResult calculateCorrelationAndRSquared(List<Double> returnsA, List<Double> returnsB) {
        if (returnsA == null || returnsB == null || returnsA.size() < 3 || returnsA.size() != returnsB.size()) {
            return new CorrelationResult(0.0, 0.0);
        }

        int n = returnsA.size();
        double sumA = 0.0;
        double sumB = 0.0;
        for (int i = 0; i < n; i++) {
            sumA += returnsA.get(i);
            sumB += returnsB.get(i);
        }

        double meanA = sumA / n;
        double meanB = sumB / n;

        double cov = 0.0;
        double varA = 0.0;
        double varB = 0.0;

        for (int i = 0; i < n; i++) {
            double diffA = returnsA.get(i) - meanA;
            double diffB = returnsB.get(i) - meanB;
            cov += diffA * diffB;
            varA += diffA * diffA;
            varB += diffB * diffB;
        }

        if (varA <= 1e-14 || varB <= 1e-14) {
            return new CorrelationResult(0.0, 0.0);
        }

        double rho = cov / Math.sqrt(varA * varB);
        // Clamp rho to [-1.0, 1.0]
        rho = Math.max(-1.0, Math.min(1.0, rho));

        double r2 = (rho > 0.0) ? Math.min(1.0, rho * rho) : 0.0;
        return new CorrelationResult(rho, r2);
    }

    /**
     * Calculates annualized volatility from a series of daily returns:
     * sigma = std(r) * sqrt(252).
     */
    public static double calculateAnnualizedVolatility(List<Double> returns) {
        if (returns == null || returns.size() < 2) {
            return 0.0;
        }
        int n = returns.size();
        double sum = 0.0;
        for (double r : returns) {
            sum += r;
        }
        double mean = sum / n;

        double varSum = 0.0;
        for (double r : returns) {
            double d = r - mean;
            varSum += d * d;
        }
        double sampleVariance = varSum / (n - 1);
        double std = Math.sqrt(sampleVariance);
        return std * Math.sqrt(252.0);
    }

    /**
     * Calculates annualized Sharpe ratio without risk-free rate (r_f = 0):
     * Sharpe = (Mean(r) / Std(r)) * sqrt(252).
     */
    public static double calculateSharpeRatio(List<Double> returns) {
        if (returns == null || returns.size() < 2) {
            return 0.0;
        }
        int n = returns.size();
        double sum = 0.0;
        for (double r : returns) {
            sum += r;
        }
        double mean = sum / n;

        double varSum = 0.0;
        for (double r : returns) {
            double d = r - mean;
            varSum += d * d;
        }
        double sampleVariance = varSum / (n - 1);
        double std = Math.sqrt(sampleVariance);
        if (std <= 1e-14) {
            return 0.0;
        }
        return (mean / std) * Math.sqrt(252.0);
    }

    /**
     * Calculates Kaufman Efficiency Ratio (KER):
     * KER = |P(t) - P(t - 365d)| / sum(|P(i) - P(i-1)|)
     */
    public static double calculateKaufmanEfficiencyRatio(List<BigDecimal> sortedPrices) {
        if (sortedPrices == null || sortedPrices.size() < 2) {
            return 0.0;
        }
        BigDecimal pFirst = sortedPrices.get(0);
        BigDecimal pLast = sortedPrices.get(sortedPrices.size() - 1);
        if (pFirst == null || pLast == null) {
            return 0.0;
        }

        double netDisplacement = Math.abs(pLast.subtract(pFirst).doubleValue());
        double totalPath = 0.0;

        for (int i = 1; i < sortedPrices.size(); i++) {
            BigDecimal prev = sortedPrices.get(i - 1);
            BigDecimal curr = sortedPrices.get(i);
            if (prev != null && curr != null) {
                totalPath += Math.abs(curr.subtract(prev).doubleValue());
            }
        }

        if (totalPath <= 1e-12) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, netDisplacement / totalPath));
    }

    /**
     * Calculates 12-1 Month Momentum:
     * MOM(12-1) = [P(T - 30d) / P(T - 365d)] - 1.
     */
    public static double calculateMomentum12_1(BigDecimal p30d, BigDecimal p365d) {
        if (p30d == null || p365d == null || p365d.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return p30d.divide(p365d, 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).doubleValue();
    }

    /**
     * Calculates continuous percentile ranks in [0.0, 1.0] for a collection of items.
     * Uses fractional / average ranking to break ties fairly.
     *
     * @param items List of candidate items
     * @param factorExtractor Function extracting the factor value
     * @param ascending True if higher factor value is better (higher rank)
     */
    public static <T> Map<T, Double> calculatePercentileRanks(List<T> items,
                                                             ToDoubleFunction<T> factorExtractor,
                                                             boolean ascending) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        if (items.size() == 1) {
            return Map.of(items.get(0), 1.0);
        }

        int n = items.size();
        // Sort items by extracted factor value
        List<T> sorted = new ArrayList<>(items);
        Comparator<T> comparator = Comparator.comparingDouble(factorExtractor);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        sorted.sort(comparator);

        Map<T, Double> percentileMap = new IdentityHashMap<>();

        int i = 0;
        while (i < n) {
            int j = i;
            double valI = factorExtractor.applyAsDouble(sorted.get(i));
            // Find all ties with the same value
            while (j < n && Math.abs(factorExtractor.applyAsDouble(sorted.get(j)) - valI) < 1e-9) {
                j++;
            }
            // Average rank position between i and j - 1 (1-based: i + 1 to j)
            double avgRank = ((i + 1) + j) / 2.0;
            // Percentile rank: (avgRank - 1) / (n - 1) -> maps from 0.0 to 1.0
            double pct = (avgRank - 1.0) / (n - 1.0);
            pct = Math.max(0.0, Math.min(1.0, pct));

            for (int k = i; k < j; k++) {
                percentileMap.put(sorted.get(k), pct);
            }
            i = j;
        }

        return percentileMap;
    }
}
