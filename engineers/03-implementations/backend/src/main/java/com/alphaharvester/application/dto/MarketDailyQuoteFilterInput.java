package com.alphaharvester.application.dto;

public record MarketDailyQuoteFilterInput(
        String ticker,
        String startDate,
        String endDate
) {
}

