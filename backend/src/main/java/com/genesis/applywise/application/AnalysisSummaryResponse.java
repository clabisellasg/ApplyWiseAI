package com.genesis.applywise.application;

public record AnalysisSummaryResponse(
        Long id,
        int score,
        String provider,
        String model
) {
}
