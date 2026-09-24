package com.alphaharvester.application.dto;

import com.alphaharvester.domain.model.CrisisLevel;
import com.alphaharvester.domain.model.MacroState;

public record MacroRegimeAssessment(
        MacroState macroState,
        double recommendedEquityRatio,
        double recommendedBondRatio,
        double usCorporateBondYield,
        String assessmentSummary,
        CrisisLevel crisisLevel
) {
}

