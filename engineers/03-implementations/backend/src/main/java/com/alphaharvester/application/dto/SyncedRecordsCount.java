package com.alphaharvester.application.dto;

public record SyncedRecordsCount(
        int etfAssetsCount,
        int dailyQuotesCount,
        int macroYieldSnapshotsCount,
        int dcaPopularityRanksCount,
        int dividendAnnouncementsCount,
        int corporateActionsCount
) {
}

