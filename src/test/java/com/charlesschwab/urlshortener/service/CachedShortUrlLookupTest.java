package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link CachedShortUrlLookup#findByCode(String)} is actually intercepted
 * by Spring's caching proxy (a bean-based test, not a plain unit test, because caching
 * only works through the proxy Spring creates around the bean): repeated lookups for the
 * same code hit the repository once, lookups for different codes each hit the
 * repository, and misses are never cached.
 */
@SpringBootTest(classes = CachedShortUrlLookupTest.TestConfig.class)
class CachedShortUrlLookupTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("shortUrlsByCode");
        }

        @Bean
        ShortUrlRepository shortUrlRepository() {
            return mock(ShortUrlRepository.class);
        }

        @Bean
        CachedShortUrlLookup cachedShortUrlLookup(ShortUrlRepository shortUrlRepository) {
            return new CachedShortUrlLookup(shortUrlRepository);
        }
    }

    @Autowired
    private CachedShortUrlLookup cachedShortUrlLookup;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Test
    void cachesRepeatedLookupsForTheSameCode() {
        ShortUrl shortUrl = new ShortUrl("aB91xY1", "https://example.com", "https://example.com", Instant.now());
        when(shortUrlRepository.findByCode("aB91xY1")).thenReturn(Optional.of(shortUrl));

        ShortUrl first = cachedShortUrlLookup.findByCode("aB91xY1");
        ShortUrl second = cachedShortUrlLookup.findByCode("aB91xY1");
        ShortUrl third = cachedShortUrlLookup.findByCode("aB91xY1");

        assertThat(first).isEqualTo(second).isEqualTo(third);
        verify(shortUrlRepository, times(1)).findByCode("aB91xY1");
    }

    @Test
    void doesNotCacheMissesForAnUnknownCode() {
        when(shortUrlRepository.findByCode("missing")).thenReturn(Optional.empty());

        assertThat(cachedShortUrlLookup.findByCode("missing")).isNull();
        assertThat(cachedShortUrlLookup.findByCode("missing")).isNull();

        verify(shortUrlRepository, times(2)).findByCode("missing");
    }

    @Test
    void cachesDifferentCodesIndependently() {
        ShortUrl first = new ShortUrl("codeAAA", "https://example.com/a", "https://example.com/a", Instant.now());
        ShortUrl second = new ShortUrl("codeBBB", "https://example.com/b", "https://example.com/b", Instant.now());
        when(shortUrlRepository.findByCode("codeAAA")).thenReturn(Optional.of(first));
        when(shortUrlRepository.findByCode("codeBBB")).thenReturn(Optional.of(second));

        assertThat(cachedShortUrlLookup.findByCode("codeAAA").getCode()).isEqualTo("codeAAA");
        assertThat(cachedShortUrlLookup.findByCode("codeBBB").getCode()).isEqualTo("codeBBB");
        cachedShortUrlLookup.findByCode("codeAAA");
        cachedShortUrlLookup.findByCode("codeBBB");

        verify(shortUrlRepository, times(1)).findByCode("codeAAA");
        verify(shortUrlRepository, times(1)).findByCode("codeBBB");
    }
}
