package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates a submitted URL per FR-2 and assumption A1: it must be present, no longer
 * than the documented maximum, syntactically well-formed, absolute, and restricted to the
 * {@code http}/{@code https} schemes.
 */
@Component
public class UrlValidator {

    static final int MAX_URL_LENGTH = 2048;

    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("url must not be blank");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("url exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }

        URI uri = parse(url);

        if (!uri.isAbsolute()) {
            throw new InvalidUrlException("url must be an absolute http or https URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("url must be an absolute http or https URL");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must be an absolute http or https URL");
        }
    }

    private URI parse(String url) {
        try {
            return new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a well-formed URI");
        }
    }
}
