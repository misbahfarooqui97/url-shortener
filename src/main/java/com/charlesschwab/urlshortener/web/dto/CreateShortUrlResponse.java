package com.charlesschwab.urlshortener.web.dto;

import java.time.Instant;

/**
 * Response body for {@code POST /api/v1/short-urls}, matching
 * {@code docs/specs/api-specification.md} section 1.
 */
public record CreateShortUrlResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt) {
}
