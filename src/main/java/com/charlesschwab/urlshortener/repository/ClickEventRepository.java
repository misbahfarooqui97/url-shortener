package com.charlesschwab.urlshortener.repository;

import com.charlesschwab.urlshortener.domain.ClickEvent;
import com.charlesschwab.urlshortener.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence access for {@link ClickEvent}. Query methods back the FR-6 analytics
 * requirement: total click count plus first/last access timestamps for a given short URL.
 */
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortUrl(ShortUrl shortUrl);

    Optional<ClickEvent> findFirstByShortUrlOrderByAccessedAtAsc(ShortUrl shortUrl);

    Optional<ClickEvent> findFirstByShortUrlOrderByAccessedAtDesc(ShortUrl shortUrl);
}
