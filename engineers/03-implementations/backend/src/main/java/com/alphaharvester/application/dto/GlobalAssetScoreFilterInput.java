package com.alphaharvester.application.dto;

import com.alphaharvester.domain.model.CandidateAssetClass;

public record GlobalAssetScoreFilterInput(
        CandidateAssetClass assetClass,
        String evaluationDate,
        Boolean isQualified
) {
}

