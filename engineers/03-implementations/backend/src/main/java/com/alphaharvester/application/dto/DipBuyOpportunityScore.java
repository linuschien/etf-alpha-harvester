package com.alphaharvester.application.dto;

public record DipBuyOpportunityScore(
        String ticker,
        double compositeScore,
        String starRating,
        String winRateEstimate,
        String recommendation,
        double bollingerScore,
        double fibonacciScore,
        double maSupportScore,
        double panicScore
) {
}

