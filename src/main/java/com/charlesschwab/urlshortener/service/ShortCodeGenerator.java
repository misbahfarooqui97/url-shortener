package com.charlesschwab.urlshortener.service;

/**
 * Generates candidate short codes. Implementations must not derive the code from the URL
 * content (ADR-002): callers are responsible for checking the candidate for collisions and
 * retrying, since this interface returns a single candidate per call.
 */
public interface ShortCodeGenerator {

    String generate();
}
