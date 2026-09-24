package com.alphaharvester.application.dto;

public record MarketDataSyncResponse(
        String status,
        String message,
        String executedAt,
        SyncedRecordsCount syncedRecords
) {
}

