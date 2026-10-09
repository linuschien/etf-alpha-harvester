package com.alphaharvester.adapter.in.scheduler;

import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.domain.model.SyncScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Inbound scheduling adapter that triggers daily market data synchronization
 * at 12:00 PM Taiwan time (Asia/Taipei).
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.market-data-sync.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncScheduler.class);

    private final MarketDataSyncUseCase syncUseCase;

    public MarketDataSyncScheduler(MarketDataSyncUseCase syncUseCase) {
        this.syncUseCase = syncUseCase;
    }

    /**
     * Executes daily market data synchronization at 12:00 PM Taiwan Standard Time (TST, Asia/Taipei).
     * Configurable via 'app.scheduler.market-data-sync.cron' and 'app.scheduler.market-data-sync.zone'.
     */
    @Scheduled(
            cron = "${app.scheduler.market-data-sync.cron:0 0 12 * * *}",
            zone = "${app.scheduler.market-data-sync.zone:Asia/Taipei}"
    )
    public MarketDataSyncResponse syncMarketDataDaily() {
        log.info("Scheduled market data synchronization triggered (Asia/Taipei 12:00)...");
        MarketDataSyncRequest request = new MarketDataSyncRequest(SyncScope.ALL, true);

        return syncUseCase.syncMarketData(request)
                .doOnSuccess(resp -> log.info("Scheduled market data sync completed: status={}, message={}",
                        resp != null ? resp.status() : "UNKNOWN",
                        resp != null ? resp.message() : "No message"))
                .doOnError(e -> log.error("Scheduled market data sync failed: {}", e.getMessage(), e))
                .onErrorResume(e -> Mono.empty())
                .block();
    }
}

