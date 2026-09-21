package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers FR-2 and assumption A1: only well-formed, absolute http/https URLs within the
 * documented length limit are accepted.
 */
class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://example.com/products/123",
            "https://example.com:8443/path?query=1&other=2",
            "http://sub.example.co.uk/a/b/c"
    })
    void acceptsValidAbsoluteHttpAndHttpsUrls(String url) {
        assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullUrl() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> validator.validate("   "))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "ftp://example.com/file.txt",
            "example.com",
            "/relative/path",
            "not a url at all"
    })
    void rejectsDisallowedSchemesAndMalformedUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlExceedingMaximumLength() {
        String longUrl = "https://example.com/" + "a".repeat(UrlValidator.MAX_URL_LENGTH);

        assertThatThrownBy(() -> validator.validate(longUrl))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("exceeds maximum length");
    }
}
