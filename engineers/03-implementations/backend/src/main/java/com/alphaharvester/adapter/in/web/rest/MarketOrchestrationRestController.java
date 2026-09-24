package com.alphaharvester.adapter.in.web.rest;

import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.application.port.in.MarketDataSyncUseCase;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public class MarketOrchestrationRestController {

    private static final Logger log = LoggerFactory.getLogger(MarketOrchestrationRestController.class);

    private final MarketDataSyncUseCase syncUseCase;
    private final GlobalAssetScoreEvaluationUseCase scoreEvaluationUseCase;

    public MarketOrchestrationRestController(MarketDataSyncUseCase syncUseCase,
                                             GlobalAssetScoreEvaluationUseCase scoreEvaluationUseCase) {
        this.syncUseCase = syncUseCase;
        this.scoreEvaluationUseCase = scoreEvaluationUseCase;
    }

    @PostMapping("/marketData:sync")
    public Mono<ResponseEntity<MarketDataSyncResponse>> syncMarketData(@Valid @RequestBody(required = false) MarketDataSyncRequest request) {
        MarketDataSyncRequest req = request != null ? request : new MarketDataSyncRequest(null, null);
        log.info("REST Ingress: POST /api/v1/marketData:sync initiated with scope: {}", req.scope());

        return syncUseCase.syncMarketData(req)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("REST Ingress error on /marketData:sync: {}", e.getMessage(), e));
    }

    @PostMapping("/globalAssetScores:evaluate")
    public Mono<ResponseEntity<GlobalAssetScoreEvaluationResponse>> evaluateGlobalAssetScores() {
        log.info("REST Ingress: POST /api/v1/globalAssetScores:evaluate initiated");

        return scoreEvaluationUseCase.evaluateGlobalAssetScores()
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("REST Ingress error on /globalAssetScores:evaluate: {}", e.getMessage(), e));
    }
}

