package com.charlesschwab.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Default {@link ShortCodeGenerator}: a random, fixed-length, base62 (alphanumeric) code.
 * Random generation (rather than a hash or sequence derived from the URL) is the ADR-002
 * decision; collision handling and retry bounds are the caller's responsibility
 * ({@link ShortUrlService}).
 */
@Component
public class RandomShortCodeGenerator implements ShortCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
