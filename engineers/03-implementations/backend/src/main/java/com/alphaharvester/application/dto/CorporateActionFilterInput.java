package com.alphaharvester.application.dto;

import com.alphaharvester.domain.model.CorporateActionType;

public record CorporateActionFilterInput(
        String ticker,
        CorporateActionType actionType
) {
}

