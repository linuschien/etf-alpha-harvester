package com.alphaharvester.web;

import com.alphaharvester.adapter.in.web.rest.MarketOrchestrationRestController;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.dto.SyncedRecordsCount;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import com.alphaharvester.domain.model.SyncScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOrchestrationRestControllerTest {

    @Mock
    private MarketDataSyncUseCase syncUseCase;

    @Mock
    private GlobalAssetScoreEvaluationUseCase scoreEvaluationUseCase;

    private MarketOrchestrationRestController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketOrchestrationRestController(syncUseCase, scoreEvaluationUseCase);
    }

    @Test
    @DisplayName("Should return 200 OK on syncMarketData POST endpoint")
    void shouldReturnOkOnSyncMarketData() {
        MarketDataSyncResponse mockResponse = new MarketDataSyncResponse(
                "SUCCESS",
                "Synced successfully",
                LocalDateTime.now().toString(),
                new SyncedRecordsCount(5, 4, 1, 1, 1, 0)
        );
        when(syncUseCase.syncMarketData(any())).thenReturn(Mono.just(mockResponse));

        StepVerifier.create(controller.syncMarketData(new MarketDataSyncRequest(SyncScope.ALL, true)))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().status()).isEqualTo("SUCCESS");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 200 OK on evaluateGlobalAssetScores POST endpoint")
    void shouldReturnOkOnEvaluateGlobalAssetScores() {
        GlobalAssetScoreEvaluationResponse mockResponse = new GlobalAssetScoreEvaluationResponse(
                "SUCCESS",
                "Evaluated",
                LocalDateTime.now().toString(),
                5, 2, 2, 1
        );
        when(scoreEvaluationUseCase.evaluateGlobalAssetScores()).thenReturn(Mono.just(mockResponse));

        StepVerifier.create(controller.evaluateGlobalAssetScores())
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().evaluatedCandidatesCount()).isEqualTo(5);
                })
                .verifyComplete();
    }
}

