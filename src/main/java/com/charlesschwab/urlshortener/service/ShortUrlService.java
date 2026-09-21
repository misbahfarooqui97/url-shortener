package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.domain.ClickEvent;
import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.exception.ShortCodeGenerationException;
import com.charlesschwab.urlshortener.exception.ShortUrlNotFoundException;
import com.charlesschwab.urlshortener.repository.ClickEventRepository;
import com.charlesschwab.urlshortener.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Business rules for the URL shortener, independent of the HTTP layer:
 * <ul>
 *   <li>Create a short URL, validating the input (FR-2) and reusing an existing active
 *       code for an identical normalized URL (ADR-001) before minting a new one
 *       (ADR-002, with a bounded collision retry).</li>
 *   <li>Resolve a short code to its original URL and record the access event
 *       (FR-3, FR-5).</li>
 *   <li>Report aggregate analytics for a short code (FR-6).</li>
 * </ul>
 * Unknown codes are reported via {@link ShortUrlNotFoundException} (FR-4) so the HTTP
 * layer can translate it to a {@code 404} without this service knowing about HTTP.
 * Code lookups for {@link #resolve(String)} and {@link #getAnalytics(String)} go through
 * {@link CachedShortUrlLookup} (see its Javadoc for the caching strategy and trade-offs).
 */
@Service
public class ShortUrlService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlValidator urlValidator;
    private final UrlNormalizer urlNormalizer;
    private final CachedShortUrlLookup cachedShortUrlLookup;
    private final Clock clock;

    public ShortUrlService(
            ShortUrlRepository shortUrlRepository,
            ClickEventRepository clickEventRepository,
            ShortCodeGenerator shortCodeGenerator,
            UrlValidator urlValidator,
            UrlNormalizer urlNormalizer,
            CachedShortUrlLookup cachedShortUrlLookup,
            Clock clock) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlValidator = urlValidator;
        this.urlNormalizer = urlNormalizer;
        this.cachedShortUrlLookup = cachedShortUrlLookup;
        this.clock = clock;
    }

    public ShortUrl createShortUrl(String originalUrl) {
        urlValidator.validate(originalUrl);
        String normalizedUrl = urlNormalizer.normalize(originalUrl);

        // Try up to 5 times to create or retrieve the short URL.
        // On race condition (both threads try to insert same normalized URL),
        // the second one will hit a constraint violation and retry-fetch.
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return createShortUrlInternal(normalizedUrl, originalUrl);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Race condition: another thread inserted the same normalized URL.
                // Try to fetch it. If found, return it. Otherwise, retry creation.
                Optional<ShortUrl> existing = shortUrlRepository.findByNormalizedUrlAndActiveTrue(normalizedUrl);
                if (existing.isPresent()) {
                    return existing.get();
                }
                // If not found, loop again (might be a different constraint violation).
                if (attempt == 4) {
                    throw new IllegalStateException(
                            "Could not create or fetch normalized URL after 5 attempts", e);
                }
            }
        }
        throw new IllegalStateException("Failed to create or retrieve short URL after 5 attempts");
    }

    @Transactional
    private ShortUrl createShortUrlInternal(String normalizedUrl, String originalUrl) {
        // Check if it already exists (might have been inserted by another thread in previous attempt)
        Optional<ShortUrl> existing = shortUrlRepository.findByNormalizedUrlAndActiveTrue(normalizedUrl);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Insert new row. If constraint violation occurs, caller's retry will catch it.
        ShortUrl newUrl = new ShortUrl(generateUniqueCode(), originalUrl, normalizedUrl, Instant.now(clock));
        return shortUrlRepository.saveAndFlush(newUrl);
    }

    @Transactional
    public ShortUrl resolve(String code) {
        ShortUrl shortUrl = findActiveByCodeOrThrow(code);
        clickEventRepository.save(new ClickEvent(shortUrl, Instant.now(clock)));
        return shortUrl;
    }

    @Transactional(readOnly = true)
    public AnalyticsResult getAnalytics(String code) {
        ShortUrl shortUrl = findActiveByCodeOrThrow(code);

        long totalClicks = clickEventRepository.countByShortUrl(shortUrl);
        Instant firstAccessedAt = clickEventRepository.findFirstByShortUrlOrderByAccessedAtAsc(shortUrl)
                .map(ClickEvent::getAccessedAt)
                .orElse(null);
        Instant lastAccessedAt = clickEventRepository.findFirstByShortUrlOrderByAccessedAtDesc(shortUrl)
                .map(ClickEvent::getAccessedAt)
                .orElse(null);

        return new AnalyticsResult(shortUrl.getCode(), shortUrl.getOriginalUrl(), totalClicks, firstAccessedAt, lastAccessedAt);
    }

    private ShortUrl findActiveByCodeOrThrow(String code) {
        ShortUrl shortUrl = cachedShortUrlLookup.findByCode(code);
        if (shortUrl == null) {
            throw new ShortUrlNotFoundException(code);
        }
        return shortUrl;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = shortCodeGenerator.generate();
            if (!shortUrlRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new ShortCodeGenerationException();
    }
}

