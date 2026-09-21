package com.charlesschwab.urlshortener.repository;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence access for {@link ShortUrl}. Query methods back the requirements in
 * FR-1 (unique short code lookup), FR-3 (redirect resolution), and the ADR-001 duplicate
 * reuse policy (lookup by normalized URL, restricted to active links).
 */
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    Optional<ShortUrl> findByNormalizedUrlAndActiveTrue(String normalizedUrl);

    boolean existsByCode(String code);
}
