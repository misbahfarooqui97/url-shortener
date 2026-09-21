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
 */
@Service
public class ShortUrlService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlValidator urlValidator;
    private final UrlNormalizer urlNormalizer;
    private final Clock clock;

    public ShortUrlService(
            ShortUrlRepository shortUrlRepository,
            ClickEventRepository clickEventRepository,
            ShortCodeGenerator shortCodeGenerator,
            UrlValidator urlValidator,
            UrlNormalizer urlNormalizer,
            Clock clock) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlValidator = urlValidator;
        this.urlNormalizer = urlNormalizer;
        this.clock = clock;
    }

    @Transactional
    public ShortUrl createShortUrl(String originalUrl) {
        urlValidator.validate(originalUrl);
        String normalizedUrl = urlNormalizer.normalize(originalUrl);

        return shortUrlRepository.findByNormalizedUrlAndActiveTrue(normalizedUrl)
                .orElseGet(() -> shortUrlRepository.save(
                        new ShortUrl(generateUniqueCode(), originalUrl, normalizedUrl, Instant.now(clock))));
    }

    @Transactional
    public ShortUrl resolve(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        clickEventRepository.save(new ClickEvent(shortUrl, Instant.now(clock)));
        return shortUrl;
    }

    @Transactional(readOnly = true)
    public AnalyticsResult getAnalytics(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));

        long totalClicks = clickEventRepository.countByShortUrl(shortUrl);
        Instant firstAccessedAt = clickEventRepository.findFirstByShortUrlOrderByAccessedAtAsc(shortUrl)
                .map(ClickEvent::getAccessedAt)
                .orElse(null);
        Instant lastAccessedAt = clickEventRepository.findFirstByShortUrlOrderByAccessedAtDesc(shortUrl)
                .map(ClickEvent::getAccessedAt)
                .orElse(null);

        return new AnalyticsResult(shortUrl.getCode(), shortUrl.getOriginalUrl(), totalClicks, firstAccessedAt, lastAccessedAt);
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

