package com.alphaharvester.domain.math;

import com.alphaharvester.domain.entity.MarketDailyQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure mathematical engine for calculating Technical Indicators (Moving Averages and Bollinger Bands)
 * on a time series of MarketDailyQuote instances.
 */
public final class TechnicalIndicatorCalculator {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private TechnicalIndicatorCalculator() {
        // Utility class
    }

    /**
     * Calculates rolling MA20, MA60, MA120, MA240 and 20-day Bollinger Bands (Upper, Middle, Lower)
     * for a list of daily quotes. Quotes are sorted chronologically by tradeDate ascending.
     *
     * @param quotes Raw market daily quotes (minimum 1 quote; indicators populate when sufficient history is available)
     * @return List of quotes enriched with transient technical indicator values
     */
    public static List<MarketDailyQuote> calculateIndicators(List<MarketDailyQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return Collections.emptyList();
        }

        List<MarketDailyQuote> sorted = new ArrayList<>(quotes);
        sorted.sort(Comparator.comparing(MarketDailyQuote::getTradeDate));

        int n = sorted.size();

        for (int i = 0; i < n; i++) {
            MarketDailyQuote current = sorted.get(i);

            // MA20 & Bollinger Bands (requires at least 20 trading days)
            if (i >= 19) {
                BigDecimal sum20 = BigDecimal.ZERO;
                for (int j = i - 19; j <= i; j++) {
                    sum20 = sum20.add(sorted.get(j).getClosePrice());
                }
                BigDecimal ma20 = sum20.divide(BigDecimal.valueOf(20), SCALE, ROUNDING);
                current.setMa20(ma20);
                current.setBbMiddle(ma20);

                // Bollinger Bands standard deviation: sqrt( (1/20) * sum( (close - ma20)^2 ) )
                double varianceSum = 0.0;
                double ma20Double = ma20.doubleValue();
                for (int j = i - 19; j <= i; j++) {
                    double diff = sorted.get(j).getClosePrice().doubleValue() - ma20Double;
                    varianceSum += diff * diff;
                }
                double stdDev = Math.sqrt(varianceSum / 20.0);
                BigDecimal twoStdDev = BigDecimal.valueOf(2.0 * stdDev).setScale(SCALE, ROUNDING);

                current.setBbUpper(ma20.add(twoStdDev).setScale(SCALE, ROUNDING));
                current.setBbLower(ma20.subtract(twoStdDev).setScale(SCALE, ROUNDING));
            } else {
                current.setMa20(null);
                current.setBbUpper(null);
                current.setBbMiddle(null);
                current.setBbLower(null);
            }

            // MA60 (requires at least 60 trading days)
            if (i >= 59) {
                BigDecimal sum60 = BigDecimal.ZERO;
                for (int j = i - 59; j <= i; j++) {
                    sum60 = sum60.add(sorted.get(j).getClosePrice());
                }
                current.setMa60(sum60.divide(BigDecimal.valueOf(60), SCALE, ROUNDING));
            } else {
                current.setMa60(null);
            }

            // MA120 (requires at least 120 trading days)
            if (i >= 119) {
                BigDecimal sum120 = BigDecimal.ZERO;
                for (int j = i - 119; j <= i; j++) {
                    sum120 = sum120.add(sorted.get(j).getClosePrice());
                }
                current.setMa120(sum120.divide(BigDecimal.valueOf(120), SCALE, ROUNDING));
            } else {
                current.setMa120(null);
            }

            // MA240 (requires at least 240 trading days)
            if (i >= 239) {
                BigDecimal sum240 = BigDecimal.ZERO;
                for (int j = i - 239; j <= i; j++) {
                    sum240 = sum240.add(sorted.get(j).getClosePrice());
                }
                current.setMa240(sum240.divide(BigDecimal.valueOf(240), SCALE, ROUNDING));
            } else {
                current.setMa240(null);
            }
        }

        return sorted;
    }
}
