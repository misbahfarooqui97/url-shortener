package com.charlesschwab.urlshortener.exception;

/**
 * Raised when the bounded short-code collision retry (ADR-002) is exhausted without
 * finding an unused code. The message intentionally omits attempt counts or internal
 * generator details (NFR-4) and is safe to surface directly in an API error response.
 */
public class ShortCodeGenerationException extends RuntimeException {

    public ShortCodeGenerationException() {
        super("Unable to generate a unique short code at this time. Please try again.");
    }
}
