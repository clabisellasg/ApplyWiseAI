package com.genesis.applywise.analysis;

import com.genesis.applywise.ai.AnalysisResult;

import java.time.Instant;

public record AnalysisResponse(
        Long id,
        Long resumeId,
        Long jobPostingId,
        int matchScore,
        String summary,
        AnalysisResult result,
        String provider,
        String model,
        String promptVersion,
        Instant createdAt,
        boolean cacheHit
) {
}
