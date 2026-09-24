package com.alphaharvester.application.dto;

import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;

public record GlobalAssetFilterInput(
        String ticker,
        CandidateAssetClass assetClass,
        DistributionFrequency distributionFrequency
) {
}

