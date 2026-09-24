package com.alphaharvester.application.dto;

public record MarketDataSyncResponse(
        String status,
        String message,
        String executedAt,
        SyncedRecordsCount syncedRecords,
        GatekeeperReport gatekeeperReport
) {
    public MarketDataSyncResponse(String status, String message, String executedAt, SyncedRecordsCount syncedRecords) {
        this(status, message, executedAt, syncedRecords, null);
    }
}
