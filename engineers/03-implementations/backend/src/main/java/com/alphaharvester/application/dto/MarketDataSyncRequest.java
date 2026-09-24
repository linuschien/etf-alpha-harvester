package com.alphaharvester.application.dto;

import com.alphaharvester.domain.model.SyncScope;

public record MarketDataSyncRequest(
        SyncScope scope,
        Boolean evaluateAfterSync
) {
    public MarketDataSyncRequest {
        if (scope == null) {
            scope = SyncScope.ALL;
        }
        if (evaluateAfterSync == null) {
            evaluateAfterSync = true;
        }
    }
}

