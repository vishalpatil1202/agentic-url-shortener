package com.assignment.agentic.shortener.api.dto;

import java.time.Instant;

public record ShortUrlResponse(
        String code,
        String originalUrl,
        String shortPath,
        boolean customAlias,
        boolean active,
        Instant createdAt,
        Instant expiresAt,
        long clickCount
) {
}
