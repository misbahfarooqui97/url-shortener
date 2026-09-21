package com.charlesschwab.urlshortener.exception;

/**
 * Raised when a submitted URL fails validation (FR-2): missing/blank, malformed,
 * disallowed scheme, or over the maximum accepted length. The message is safe to
 * surface directly in an API error response.
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
