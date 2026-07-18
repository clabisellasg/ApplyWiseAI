package com.genesis.applywise.job;

import java.time.Instant;

public record JobPostingResponse(
        Long id,
        String title,
        String company,
        String description,
        String sourceUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
