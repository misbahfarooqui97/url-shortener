package com.charlesschwab.urlshortener.repository;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level tests run against the configured local datastore (H2, PostgreSQL
 * compatibility mode) exactly as the application does outside of tests, per Phase 2's
 * acceptance criteria: short codes are enforced unique at the persistence layer, and links
 * can be stored and retrieved.
 */
@DataJpaTest
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Test
    void savesAndRetrievesShortUrlByCode() {
        ShortUrl shortUrl = new ShortUrl("aB91xY", "https://example.com/a", "https://example.com/a", Instant.now());
        shortUrlRepository.saveAndFlush(shortUrl);

        assertThat(shortUrlRepository.findByCode("aB91xY"))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getOriginalUrl()).isEqualTo("https://example.com/a");
                    assertThat(found.isActive()).isTrue();
                });
    }

    @Test
    void returnsEmptyForUnknownCode() {
        assertThat(shortUrlRepository.findByCode("doesNotExist")).isEmpty();
    }

    @Test
    void enforcesUniqueCodeAtThePersistenceLayer() {
        shortUrlRepository.saveAndFlush(
                new ShortUrl("dupeCode", "https://example.com/one", "https://example.com/one", Instant.now()));

        ShortUrl conflicting =
                new ShortUrl("dupeCode", "https://example.com/two", "https://example.com/two", Instant.now());

        assertThatThrownBy(() -> shortUrlRepository.saveAndFlush(conflicting))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsActiveShortUrlByNormalizedUrlForDuplicateReuse() {
        ShortUrl shortUrl =
                new ShortUrl("reuseMe", "https://example.com/reused", "https://example.com/reused", Instant.now());
        shortUrlRepository.saveAndFlush(shortUrl);

        assertThat(shortUrlRepository.findByNormalizedUrlAndActiveTrue("https://example.com/reused"))
                .isPresent()
                .get()
                .extracting(ShortUrl::getCode)
                .isEqualTo("reuseMe");
    }

    @Test
    void ignoresInactiveShortUrlWhenLookingUpByNormalizedUrl() {
        ShortUrl shortUrl = new ShortUrl(
                "inactiveOne", "https://example.com/inactive", "https://example.com/inactive", Instant.now());
        shortUrl.deactivate();
        shortUrlRepository.saveAndFlush(shortUrl);

        assertThat(shortUrlRepository.findByNormalizedUrlAndActiveTrue("https://example.com/inactive")).isEmpty();
    }
}
