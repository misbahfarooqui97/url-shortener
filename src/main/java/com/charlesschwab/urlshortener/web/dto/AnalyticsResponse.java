package com.charlesschwab.urlshortener.web.dto;

import java.time.Instant;

/**
 * Response body for {@code GET /api/v1/short-urls/{code}/analytics}, matching
 * {@code docs/specs/api-specification.md} section 3. A code with zero clicks reports
 * {@code totalClicks == 0} and {@code null} for both timestamp fields.
 */
public record AnalyticsResponse(
        String code,
        String originalUrl,
        long totalClicks,
        Instant firstAccessedAt,
        Instant lastAccessedAt) {
}
