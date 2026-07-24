package com.genesis.applywise.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record JobApplicationResponse(
        Long id,
        JobSummaryResponse job,
        ResumeSummaryResponse resume,
        AnalysisSummaryResponse analysis,
        ApplicationStatus status,
        LocalDate appliedAt,
        String nextAction,
        OffsetDateTime nextActionAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
