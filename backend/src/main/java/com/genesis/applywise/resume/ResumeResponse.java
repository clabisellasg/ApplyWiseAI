package com.genesis.applywise.resume;

import java.time.Instant;

public record ResumeResponse(
        Long id,
        String name,
        String targetRole,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
}
