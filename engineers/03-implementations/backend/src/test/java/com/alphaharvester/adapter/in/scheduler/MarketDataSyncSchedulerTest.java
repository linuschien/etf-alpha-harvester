package com.alphaharvester.adapter.in.scheduler;

import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.application.dto.SyncedRecordsCount;
import com.alphaharvester.domain.model.SyncScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataSyncSchedulerTest {

    @Mock
    private MarketDataSyncUseCase syncUseCase;

    private MarketDataSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MarketDataSyncScheduler(syncUseCase);
    }

    @Test
    @DisplayName("Should successfully trigger market data sync with SyncScope.ALL and evaluateAfterSync=true")
    void shouldTriggerSyncMarketDataSuccessfully() {
        MarketDataSyncResponse mockResponse = new MarketDataSyncResponse(
                "SUCCESS",
                "Market data synchronization completed successfully.",
                LocalDateTime.now().toString(),
                new SyncedRecordsCount(10, 50, 1, 20, 5, 2),
                null
        );

        when(syncUseCase.syncMarketData(any())).thenReturn(Mono.just(mockResponse));

        MarketDataSyncResponse response = scheduler.syncMarketDataDaily();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUCCESS");

        ArgumentCaptor<MarketDataSyncRequest> captor = ArgumentCaptor.forClass(MarketDataSyncRequest.class);
        verify(syncUseCase, times(1)).syncMarketData(captor.capture());

        MarketDataSyncRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.scope()).isEqualTo(SyncScope.ALL);
        assertThat(capturedRequest.evaluateAfterSync()).isTrue();
    }

    @Test
    @DisplayName("Should handle sync failure gracefully without throwing exception")
    void shouldHandleSyncFailureGracefullyWithoutThrowing() {
        when(syncUseCase.syncMarketData(any())).thenReturn(Mono.error(new RuntimeException("External upstream network failure")));

        MarketDataSyncResponse response = scheduler.syncMarketDataDaily();

        assertThat(response).isNull();
        verify(syncUseCase, times(1)).syncMarketData(any());
    }
}
