package com.charlesschwab.urlshortener.web.dto;

import java.time.Instant;

/**
 * The single error response shape used for every {@code 4xx}/{@code 5xx} response, per
 * the "Error format" section of {@code docs/specs/api-specification.md}. Never includes
 * stack traces, exception class names, or persistence details (NFR-4).
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp) {
}
