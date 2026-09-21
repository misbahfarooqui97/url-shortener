package com.charlesschwab.urlshortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the ADR-001 normalization rules used to detect duplicate URL submissions:
 * whitespace trimming, host case-insensitivity, default-port removal, trailing-slash
 * handling, query preservation, and fragment removal.
 */
class UrlNormalizerTest {

    private final UrlNormalizer normalizer = new UrlNormalizer();

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(normalizer.normalize("  https://example.com/path  "))
                .isEqualTo("https://example.com/path");
    }

    @Test
    void lowerCasesSchemeAndHost() {
        assertThat(normalizer.normalize("HTTPS://EXAMPLE.com/Path"))
                .isEqualTo("https://example.com/Path");
    }

    @Test
    void dropsDefaultPortForHttpAndHttps() {
        assertThat(normalizer.normalize("http://example.com:80/path"))
                .isEqualTo("http://example.com/path");
        assertThat(normalizer.normalize("https://example.com:443/path"))
                .isEqualTo("https://example.com/path");
    }

    @Test
    void keepsNonDefaultPort() {
        assertThat(normalizer.normalize("https://example.com:8443/path"))
                .isEqualTo("https://example.com:8443/path");
    }

    @Test
    void treatsEmptyPathAsRoot() {
        assertThat(normalizer.normalize("https://example.com"))
                .isEqualTo("https://example.com/");
    }

    @Test
    void stripsTrailingSlashBeyondRoot() {
        assertThat(normalizer.normalize("https://example.com/products/123/"))
                .isEqualTo("https://example.com/products/123");
    }

    @Test
    void preservesRootSlash() {
        assertThat(normalizer.normalize("https://example.com/"))
                .isEqualTo("https://example.com/");
    }

    @Test
    void preservesQueryStringExactlyIncludingOrder() {
        assertThat(normalizer.normalize("https://example.com/search?b=2&a=1"))
                .isEqualTo("https://example.com/search?b=2&a=1");
    }

    @Test
    void dropsFragment() {
        assertThat(normalizer.normalize("https://example.com/path#section"))
                .isEqualTo("https://example.com/path");
    }

    @Test
    void treatsEquivalentUrlsAsIdentical() {
        String a = normalizer.normalize("HTTPS://Example.com:443/products/123/");
        String b = normalizer.normalize("https://example.com/products/123");

        assertThat(a).isEqualTo(b);
    }
}
