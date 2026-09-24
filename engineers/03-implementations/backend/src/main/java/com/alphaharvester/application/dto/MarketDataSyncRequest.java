package com.alphaharvester.application.dto;

import com.alphaharvester.domain.model.SyncScope;

public record MarketDataSyncRequest(
        SyncScope scope,
        Boolean evaluateAfterSync,
        Integer backfillDays
) {
    public MarketDataSyncRequest {
        if (scope == null) {
            scope = SyncScope.ALL;
        }
        if (evaluateAfterSync == null) {
            evaluateAfterSync = true;
        }
    }

    public MarketDataSyncRequest(SyncScope scope, Boolean evaluateAfterSync) {
        this(scope, evaluateAfterSync, null);
    }
}

