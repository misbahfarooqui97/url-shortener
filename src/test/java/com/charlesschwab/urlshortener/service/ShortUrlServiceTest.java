package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.domain.ClickEvent;
import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.exception.InvalidUrlException;
import com.charlesschwab.urlshortener.exception.ShortCodeGenerationException;
import com.charlesschwab.urlshortener.exception.ShortUrlNotFoundException;
import com.charlesschwab.urlshortener.repository.ClickEventRepository;
import com.charlesschwab.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShortUrlService}, exercising it in isolation from the HTTP layer
 * and the real datastore: repositories, the code generator, and validation/normalization
 * collaborators are all substitutable, per Phase 3's acceptance criteria (valid, invalid,
 * duplicate, and collision cases; behavior matches the API specification without any
 * controller involved).
 */
@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-21T12:00:00Z");

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private CachedShortUrlLookup cachedShortUrlLookup;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        service = new ShortUrlService(
                shortUrlRepository,
                clickEventRepository,
                shortCodeGenerator,
                new UrlValidator(),
                new UrlNormalizer(),
                cachedShortUrlLookup,
                fixedClock);
    }

    @Test
    void createsNewShortUrlForFirstTimeSubmission() {
        String originalUrl = "https://example.com/products/123";
        when(shortUrlRepository.findByNormalizedUrlAndActiveTrue(any())).thenReturn(Optional.empty());
        when(shortCodeGenerator.generate()).thenReturn("aB91xY1");
        when(shortUrlRepository.existsByCode("aB91xY1")).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.createShortUrl(originalUrl);

        assertThat(result.getCode()).isEqualTo("aB91xY1");
        assertThat(result.getOriginalUrl()).isEqualTo(originalUrl);
        assertThat(result.getNormalizedUrl()).isEqualTo("https://example.com/products/123");
        assertThat(result.getCreatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void rejectsInvalidUrlBeforeTouchingRepository() {
        assertThatThrownBy(() -> service.createShortUrl("not-a-url"))
                .isInstanceOf(InvalidUrlException.class);

        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void reusesExistingActiveCodeForDuplicateNormalizedUrl() {
        ShortUrl existing = new ShortUrl("existingC", "https://example.com/products/123",
                "https://example.com/products/123", FIXED_INSTANT.minusSeconds(3600));
        when(shortUrlRepository.findByNormalizedUrlAndActiveTrue("https://example.com/products/123"))
                .thenReturn(Optional.of(existing));

        ShortUrl result = service.createShortUrl("https://example.com/products/123");

        assertThat(result).isSameAs(existing);
        verify(shortUrlRepository, never()).save(any());
        verify(shortCodeGenerator, never()).generate();
    }

    @Test
    void retriesCodeGenerationOnCollisionBeforeSucceeding() {
        when(shortUrlRepository.findByNormalizedUrlAndActiveTrue(any())).thenReturn(Optional.empty());
        when(shortCodeGenerator.generate()).thenReturn("collide", "collide", "unique1");
        when(shortUrlRepository.existsByCode("collide")).thenReturn(true);
        when(shortUrlRepository.existsByCode("unique1")).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.createShortUrl("https://example.com");

        assertThat(result.getCode()).isEqualTo("unique1");
        verify(shortCodeGenerator, times(3)).generate();
    }

    @Test
    void failsAfterExhaustingBoundedCollisionRetries() {
        when(shortUrlRepository.findByNormalizedUrlAndActiveTrue(any())).thenReturn(Optional.empty());
        when(shortCodeGenerator.generate()).thenReturn("collide");
        when(shortUrlRepository.existsByCode("collide")).thenReturn(true);

        assertThatThrownBy(() -> service.createShortUrl("https://example.com"))
                .isInstanceOf(ShortCodeGenerationException.class);

        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void resolvesActiveCodeAndRecordsClickEvent() {
        ShortUrl shortUrl = new ShortUrl("aB91xY1", "https://example.com", "https://example.com", FIXED_INSTANT);
        when(cachedShortUrlLookup.findByCode("aB91xY1")).thenReturn(shortUrl);

        ShortUrl result = service.resolve("aB91xY1");

        assertThat(result).isSameAs(shortUrl);
        verify(clickEventRepository).save(argThat(event ->
                event.getShortUrl() == shortUrl && event.getAccessedAt().equals(FIXED_INSTANT)));
    }

    @Test
    void throwsNotFoundForUnknownCodeOnResolve() {
        when(cachedShortUrlLookup.findByCode("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(ShortUrlNotFoundException.class);

        verify(clickEventRepository, never()).save(any());
    }

    @Test
    void returnsAnalyticsWithClickCountAndTimestamps() {
        ShortUrl shortUrl = new ShortUrl("aB91xY1", "https://example.com", "https://example.com", FIXED_INSTANT);
        Instant first = FIXED_INSTANT.minusSeconds(120);
        Instant last = FIXED_INSTANT.minusSeconds(10);
        when(cachedShortUrlLookup.findByCode("aB91xY1")).thenReturn(shortUrl);
        when(clickEventRepository.countByShortUrl(shortUrl)).thenReturn(42L);
        when(clickEventRepository.findFirstByShortUrlOrderByAccessedAtAsc(shortUrl))
                .thenReturn(Optional.of(new ClickEvent(shortUrl, first)));
        when(clickEventRepository.findFirstByShortUrlOrderByAccessedAtDesc(shortUrl))
                .thenReturn(Optional.of(new ClickEvent(shortUrl, last)));

        AnalyticsResult result = service.getAnalytics("aB91xY1");

        assertThat(result.code()).isEqualTo("aB91xY1");
        assertThat(result.originalUrl()).isEqualTo("https://example.com");
        assertThat(result.totalClicks()).isEqualTo(42L);
        assertThat(result.firstAccessedAt()).isEqualTo(first);
        assertThat(result.lastAccessedAt()).isEqualTo(last);
    }

    @Test
    void returnsZeroClicksAndNullTimestampsForNeverAccessedCode() {
        ShortUrl shortUrl = new ShortUrl("aB91xY1", "https://example.com", "https://example.com", FIXED_INSTANT);
        when(cachedShortUrlLookup.findByCode("aB91xY1")).thenReturn(shortUrl);
        when(clickEventRepository.countByShortUrl(shortUrl)).thenReturn(0L);
        when(clickEventRepository.findFirstByShortUrlOrderByAccessedAtAsc(shortUrl)).thenReturn(Optional.empty());
        when(clickEventRepository.findFirstByShortUrlOrderByAccessedAtDesc(shortUrl)).thenReturn(Optional.empty());

        AnalyticsResult result = service.getAnalytics("aB91xY1");

        assertThat(result.totalClicks()).isZero();
        assertThat(result.firstAccessedAt()).isNull();
        assertThat(result.lastAccessedAt()).isNull();
    }

    @Test
    void throwsNotFoundForUnknownCodeOnAnalytics() {
        when(cachedShortUrlLookup.findByCode("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.getAnalytics("missing"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }
}
