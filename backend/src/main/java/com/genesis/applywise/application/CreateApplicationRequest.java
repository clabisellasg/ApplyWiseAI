package com.genesis.applywise.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreateApplicationRequest(
        @NotNull @Positive Long jobPostingId,
        @Positive Long resumeId,
        @Positive Long analysisId,
        ApplicationStatus status,
        LocalDate appliedAt,
        @Size(max = 500) String nextAction,
        OffsetDateTime nextActionAt,
        @Size(max = 10000) String notes
) {
}
