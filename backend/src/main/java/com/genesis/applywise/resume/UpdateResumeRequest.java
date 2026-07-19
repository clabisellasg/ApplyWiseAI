package com.genesis.applywise.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateResumeRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String targetRole,
        @NotBlank String content
) {
}
