package com.charlesschwab.urlshortener.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers ADR-002: generated codes are a fixed-length, alphanumeric, unpredictable value
 * with no derivation from URL content (this generator never sees a URL at all).
 */
class RandomShortCodeGeneratorTest {

    private final RandomShortCodeGenerator generator = new RandomShortCodeGenerator();

    @RepeatedTest(20)
    void generatesSevenCharacterAlphanumericCode() {
        String code = generator.generate();

        assertThat(code).hasSize(7);
        assertThat(code).matches("[A-Za-z0-9]{7}");
    }

    @Test
    void generatesDistinctCodesAcrossManyCalls() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }

        // Collisions are possible but statistically negligible at this sample size for a
        // 62^7 code space; a near-1000 unique count confirms randomness, not a fixed value.
        assertThat(codes.size()).isGreaterThan(990);
    }
}
