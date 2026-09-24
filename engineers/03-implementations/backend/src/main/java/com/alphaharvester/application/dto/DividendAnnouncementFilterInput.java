package com.alphaharvester.application.dto;

public record DividendAnnouncementFilterInput(
        String ticker,
        String startDate,
        String endDate
) {
}

