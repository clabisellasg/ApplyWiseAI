package com.genesis.applywise.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateJobRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String company,
        @NotBlank String description,
        @Size(max = 2048) String sourceUrl
) {
}
