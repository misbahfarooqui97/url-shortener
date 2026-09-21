package com.charlesschwab.urlshortener.service;

import java.time.Instant;

/**
 * Aggregate analytics for a single short code (FR-6): total click count plus first/last
 * access timestamps. A code with zero clicks has {@code totalClicks == 0} and both
 * timestamps {@code null}, matching the documented API response in
 * {@code docs/specs/api-specification.md}.
 */
public record AnalyticsResult(
        String code,
        String originalUrl,
        long totalClicks,
        Instant firstAccessedAt,
        Instant lastAccessedAt) {
}
