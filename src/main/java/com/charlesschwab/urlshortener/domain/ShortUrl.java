package com.charlesschwab.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistent record of a shortened URL.
 *
 * <p>{@code code} is the public short code (ADR-002: randomly generated, checked for
 * collisions before persistence). {@code normalizedUrl} backs the duplicate-submission
 * policy in ADR-001: an identical, normalized, active URL reuses its existing code instead
 * of minting a new one. {@code originalUrl} is preserved as submitted for display/redirect.
 */
@Entity
@Table(
        name = "short_urls",
        indexes = {
                @Index(name = "idx_short_urls_code", columnList = "code", unique = true),
                @Index(name = "idx_short_urls_normalized_url_active", columnList = "normalized_url, active")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_short_urls_normalized_url",
                        columnNames = {"normalized_url"})
        }
)
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    /**
     * Normalized form of {@link #originalUrl} used to detect and reuse duplicates per ADR-001.
     * Not unique at the schema level on its own: only one row may be both normalized-equal and
     * active at a time, which the service layer enforces before insert.
     */
    @Column(name = "normalized_url", nullable = false, length = 2048)
    private String normalizedUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ShortUrl() {
        // required by JPA
    }

    public ShortUrl(String code, String originalUrl, String normalizedUrl, Instant createdAt) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl must not be null");
        this.normalizedUrl = Objects.requireNonNull(normalizedUrl, "normalizedUrl must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShortUrl shortUrl)) {
            return false;
        }
        return id != null && id.equals(shortUrl.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
