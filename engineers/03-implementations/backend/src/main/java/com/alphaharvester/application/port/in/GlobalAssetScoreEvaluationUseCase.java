package com.alphaharvester.application.port.in;

import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import reactor.core.publisher.Mono;

public interface GlobalAssetScoreEvaluationUseCase {
    Mono<GlobalAssetScoreEvaluationResponse> evaluateGlobalAssetScores();
    Mono<GlobalAssetScoreEvaluationResponse> evaluateGlobalAssetScores(String yearMonth, boolean force);
}
