package com.alphaharvester.application.service;

import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.DipBuyOpportunityScore;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DipBuyOpportunityService {

    private static final Logger log = LoggerFactory.getLogger(DipBuyOpportunityService.class);

    private final MarketDailyQuoteRepository quoteRepository;

    public DipBuyOpportunityService(MarketDailyQuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }

    public Mono<DipBuyOpportunityScore> calculateDipBuyOpportunity(String ticker) {
        return quoteRepository.findByTickerOrderByTradeDateDesc(ticker)
                .take(252)
                .collectList()
                .filter(quotes -> !quotes.isEmpty())
                .zipWith(
                        quoteRepository.findByTickerOrderByTradeDateDesc("^VIX")
                                .take(1)
                                .next()
                                .filter(q -> q.getClosePrice() != null && q.getClosePrice().compareTo(BigDecimal.ZERO) > 0)
                                .map(q -> q.getClosePrice().doubleValue())
                )
                .map(tuple -> evaluatePure(ticker, tuple.getT1(), tuple.getT2()))
                .doOnError(e -> log.error("Error calculating dip-buying score for ticker {}: {}", ticker, e.getMessage(), e));
    }

    public DipBuyOpportunityScore evaluatePure(String ticker, List<MarketDailyQuote> quotes, double vix) {
        if (quotes == null || quotes.isEmpty()) {
            return null;
        }

        double currentPrice = quotes.get(0).getClosePrice().doubleValue();

        // 1. Bollinger Band Calculation (20-day MA and standard deviation)
        int bbPeriod = Math.min(20, quotes.size());
        double sum20 = 0.0;
        for (int i = 0; i < bbPeriod; i++) {
            sum20 += quotes.get(i).getClosePrice().doubleValue();
        }
        double ma20 = sum20 / bbPeriod;

        double varianceSum = 0.0;
        for (int i = 0; i < bbPeriod; i++) {
            double diff = quotes.get(i).getClosePrice().doubleValue() - ma20;
            varianceSum += diff * diff;
        }
        double stdDev = Math.sqrt(varianceSum / bbPeriod);
        double upperBand = ma20 + 2.0 * stdDev;
        double lowerBand = ma20 - 2.0 * stdDev;

        double percentB = (upperBand != lowerBand) ? (currentPrice - lowerBand) / (upperBand - lowerBand) : 0.5;

        double bollingerScore;
        if (percentB <= 0.0) {
            bollingerScore = 30.0;
        } else if (percentB <= 0.15) {
            bollingerScore = 20.0;
        } else if (percentB <= 0.30) {
            bollingerScore = 10.0;
        } else {
            bollingerScore = 0.0;
        }

        // 2. Fibonacci 52-Week Drawdown Calculation
        double max52w = currentPrice;
        for (MarketDailyQuote q : quotes) {
            if (q.getHighPrice() != null && q.getHighPrice().doubleValue() > max52w) {
                max52w = q.getHighPrice().doubleValue();
            } else if (q.getClosePrice().doubleValue() > max52w) {
                max52w = q.getClosePrice().doubleValue();
            }
        }
        double drawdown = max52w > 0 ? (currentPrice - max52w) / max52w : 0.0;
        double absDrawdown = Math.abs(drawdown);

        double fibonacciScore;
        if (absDrawdown >= 0.382) {
            fibonacciScore = 25.0;
        } else if (absDrawdown >= 0.236) {
            fibonacciScore = 20.0;
        } else if (absDrawdown >= 0.146) {
            fibonacciScore = 12.0;
        } else {
            fibonacciScore = 0.0;
        }

        // 3. Moving Average Support Score (60MA, 120MA, 240MA)
        double ma60 = calculateMa(quotes, 60);
        double ma120 = calculateMa(quotes, 120);
        double ma240 = calculateMa(quotes, 240);

        double maSupportScore;
        if (ma240 > 0 && currentPrice < ma240 && ((currentPrice - ma240) / ma240) <= -0.05) {
            maSupportScore = 25.0;
        } else if (ma120 > 0 && (currentPrice < ma120 || (ma240 > 0 && Math.abs(currentPrice - ma240) / ma240 < 0.02))) {
            maSupportScore = 18.0;
        } else if (ma60 > 0 && (currentPrice < ma60 || (ma120 > 0 && Math.abs(currentPrice - ma120) / ma120 < 0.02))) {
            maSupportScore = 12.0;
        } else {
            maSupportScore = 0.0;
        }

        // 4. Panic Score (VIX)
        double panicScore;
        if (vix >= 35.0) {
            panicScore = 20.0;
        } else if (vix >= 30.0) {
            panicScore = 15.0;
        } else if (vix >= 25.0) {
            panicScore = 8.0;
        } else {
            panicScore = 0.0;
        }

        double totalScore = bollingerScore + fibonacciScore + maSupportScore + panicScore;

        String starRating;
        String winRate;
        String recommendation;

        if (totalScore >= 80.0) {
            starRating = "🟢 【五星黃金坑】";
            winRate = "≥ 90%";
            recommendation = "四維共振齊備！出現流動性錯殺超值甜蜜點，強烈建議調用交割戶停利閒置資金果斷單筆加碼 1 整張！";
        } else if (totalScore >= 60.0) {
            starRating = "🟢 【四星超跌區】";
            winRate = "75% ~ 85%";
            recommendation = "具備高度安全邊際，回檔幅度顯著，建議可分批佈局或於下期提高定期定額扣款額度。";
        } else if (totalScore >= 40.0) {
            starRating = "🟡 【三星平穩區】";
            winRate = "60% ~ 70%";
            recommendation = "常態健康回檔，市場波動在預期範圍內，嚴格維持紀律，日常定期定額靜默扣款即可。";
        } else {
            starRating = "⚪ 【低星觀望區】";
            winRate = "估值偏高";
            recommendation = "市場處於常態震盪或偏熱階段，嚴禁單筆追高加碼，避免高基期接刀。";
        }

        return new DipBuyOpportunityScore(
                ticker,
                Math.round(totalScore * 10.0) / 10.0,
                starRating,
                winRate,
                recommendation,
                bollingerScore,
                fibonacciScore,
                maSupportScore,
                panicScore
        );
    }

    private double calculateMa(List<MarketDailyQuote> quotes, int period) {
        int count = Math.min(period, quotes.size());
        if (count == 0) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            sum += quotes.get(i).getClosePrice().doubleValue();
        }
        return sum / count;
    }
}

