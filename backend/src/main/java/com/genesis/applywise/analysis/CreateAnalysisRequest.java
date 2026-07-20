package com.genesis.applywise.analysis;

import jakarta.validation.constraints.NotNull;

public record CreateAnalysisRequest(
        @NotNull Long resumeId,
        @NotNull Long jobPostingId
) {
}
