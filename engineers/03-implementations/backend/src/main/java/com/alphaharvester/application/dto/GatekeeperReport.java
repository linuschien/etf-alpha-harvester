package com.alphaharvester.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GatekeeperReport(
        String status, // "PASS" or "HALT"
        String message,
        LocalDateTime checkedAt,
        int checkedCandidatesCount,
        boolean macroYieldValid,
        List<String> violations
) {
    public boolean isPassed() {
        return "PASS".equalsIgnoreCase(status);
    }
}
