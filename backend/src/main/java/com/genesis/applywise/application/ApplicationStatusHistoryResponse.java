package com.genesis.applywise.application;

import java.time.Instant;

public record ApplicationStatusHistoryResponse(
        Long id,
        ApplicationStatus previousStatus,
        ApplicationStatus newStatus,
        Instant changedAt
) {
}
