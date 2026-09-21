package com.charlesschwab.urlshortener.exception;

/**
 * Raised when a short code does not resolve to an active {@code ShortUrl} (FR-4): used by
 * both the redirect and analytics lookups. The message is safe to surface directly in an
 * API error response.
 */
public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String code) {
        super("No short URL found for code '" + code + "'");
    }
}
