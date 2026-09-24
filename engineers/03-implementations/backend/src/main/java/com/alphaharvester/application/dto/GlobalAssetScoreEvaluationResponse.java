package com.alphaharvester.application.dto;

public record GlobalAssetScoreEvaluationResponse(
        String status,
        String message,
        String executedAt,
        int evaluatedCandidatesCount,
        int coreCount,
        int satelliteCount,
        int defensiveCount
) {
}

