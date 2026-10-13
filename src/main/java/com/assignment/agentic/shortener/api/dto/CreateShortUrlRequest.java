package com.assignment.agentic.shortener.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateShortUrlRequest(
        @NotBlank String url,
        String alias,
        Instant expiresAt
) {
}
