package com.charlesschwab.urlshortener.repository;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Persistence access for {@link ShortUrl}. Query methods back the requirements in
 * FR-1 (unique short code lookup), FR-3 (redirect resolution), and the ADR-001 duplicate
 * reuse policy (lookup by normalized URL, restricted to active links).
 */
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    Optional<ShortUrl> findByNormalizedUrlAndActiveTrue(String normalizedUrl);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShortUrl s WHERE s.normalizedUrl = :normalizedUrl AND s.active = true")
    Optional<ShortUrl> findByNormalizedUrlAndActiveTrueWithLock(@Param("normalizedUrl") String normalizedUrl);

    boolean existsByCode(String code);
}
