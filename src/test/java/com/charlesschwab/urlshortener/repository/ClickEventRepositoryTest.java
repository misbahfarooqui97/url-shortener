package com.charlesschwab.urlshortener.repository;

import com.charlesschwab.urlshortener.domain.ClickEvent;
import com.charlesschwab.urlshortener.domain.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level tests for {@link ClickEventRepository}, covering the aggregate queries
 * that back the FR-6 analytics endpoint (total clicks, first/last access timestamps).
 */
@DataJpaTest
class ClickEventRepositoryTest {

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Test
    void countsClicksForAGivenShortUrl() {
        ShortUrl shortUrl =
                shortUrlRepository.saveAndFlush(new ShortUrl("count1", "https://example.com/x", "https://example.com/x", Instant.now()));
        ShortUrl otherShortUrl =
                shortUrlRepository.saveAndFlush(new ShortUrl("count2", "https://example.com/y", "https://example.com/y", Instant.now()));

        clickEventRepository.saveAndFlush(new ClickEvent(shortUrl, Instant.now()));
        clickEventRepository.saveAndFlush(new ClickEvent(shortUrl, Instant.now()));
        clickEventRepository.saveAndFlush(new ClickEvent(otherShortUrl, Instant.now()));

        assertThat(clickEventRepository.countByShortUrl(shortUrl)).isEqualTo(2);
        assertThat(clickEventRepository.countByShortUrl(otherShortUrl)).isEqualTo(1);
    }

    @Test
    void returnsZeroCountAndNoTimestampsWhenThereAreNoClicksYet() {
        ShortUrl shortUrl = shortUrlRepository.saveAndFlush(
                new ShortUrl("noClicks", "https://example.com/none", "https://example.com/none", Instant.now()));

        assertThat(clickEventRepository.countByShortUrl(shortUrl)).isZero();
        assertThat(clickEventRepository.findFirstByShortUrlOrderByAccessedAtAsc(shortUrl)).isEmpty();
        assertThat(clickEventRepository.findFirstByShortUrlOrderByAccessedAtDesc(shortUrl)).isEmpty();
    }

    @Test
    void findsFirstAndLastAccessTimestampsInChronologicalOrder() {
        ShortUrl shortUrl = shortUrlRepository.saveAndFlush(
                new ShortUrl("chrono", "https://example.com/chrono", "https://example.com/chrono", Instant.now()));

        Instant first = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant middle = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant last = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Intentionally inserted out of chronological order to prove the query orders by
        // accessedAt rather than relying on insertion order.
        clickEventRepository.saveAndFlush(new ClickEvent(shortUrl, middle));
        clickEventRepository.saveAndFlush(new ClickEvent(shortUrl, last));
        clickEventRepository.saveAndFlush(new ClickEvent(shortUrl, first));

        assertThat(clickEventRepository.findFirstByShortUrlOrderByAccessedAtAsc(shortUrl))
                .isPresent()
                .get()
                .extracting(ClickEvent::getAccessedAt)
                .isEqualTo(first);

        assertThat(clickEventRepository.findFirstByShortUrlOrderByAccessedAtDesc(shortUrl))
                .isPresent()
                .get()
                .extracting(ClickEvent::getAccessedAt)
                .isEqualTo(last);
    }
}
