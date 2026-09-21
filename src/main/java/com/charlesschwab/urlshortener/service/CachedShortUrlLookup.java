package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.repository.ShortUrlRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Caches the code-to-{@link ShortUrl} lookup used by the redirect and analytics read
 * paths, which are the highest-traffic, most latency-sensitive part of the application.
 *
 * <p>Only successful lookups are cached ({@code unless = "#result == null"}); an unknown
 * code always hits the datastore, so a code created after a prior 404 is visible on the
 * very next request without waiting for a cache entry to expire.
 *
 * <p>This is a separate Spring bean, not a method on {@link ShortUrlService}, so that
 * Spring's proxy-based caching actually intercepts the call: calling an
 * {@code @Cacheable} method on {@code this} from within the same class bypasses the
 * proxy and silently disables caching.
 *
 * <p>Cache size/TTL are configured in {@code application.properties}
 * ({@code spring.cache.caffeine.spec}). There is currently no explicit cache eviction: a
 * {@code ShortUrl}'s {@code code} and {@code originalUrl} are immutable once created, so
 * the only staleness risk is a link being deactivated while its entry is still cached.
 * This is an accepted trade-off, bounded by the TTL, and recorded in the risk log in
 * {@code docs/engineering-summary.md}.
 */
@Component
public class CachedShortUrlLookup {

    private final ShortUrlRepository shortUrlRepository;

    public CachedShortUrlLookup(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    @Cacheable(cacheNames = "shortUrlsByCode", key = "#code", unless = "#result == null")
    public ShortUrl findByCode(String code) {
        return shortUrlRepository.findByCode(code).orElse(null);
    }
}
