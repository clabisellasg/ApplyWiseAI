package com.genesis.applywise.application;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record UpdateApplicationRequest(
        @Positive Long resumeId,
        @Positive Long analysisId,
        LocalDate appliedAt,
        @Size(max = 500) String nextAction,
        OffsetDateTime nextActionAt,
        @Size(max = 10000) String notes
) {
}
