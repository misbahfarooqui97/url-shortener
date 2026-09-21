package com.charlesschwab.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Records a single successful redirect for a {@link ShortUrl}, satisfying FR-5 (record an
 * access event on every successful redirect) and backing the FR-6 analytics endpoint.
 */
@Entity
@Table(
        name = "click_events",
        indexes = {
                @Index(name = "idx_click_events_short_url_id", columnList = "short_url_id")
        }
)
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    protected ClickEvent() {
        // required by JPA
    }

    public ClickEvent(ShortUrl shortUrl, Instant accessedAt) {
        this.shortUrl = Objects.requireNonNull(shortUrl, "shortUrl must not be null");
        this.accessedAt = Objects.requireNonNull(accessedAt, "accessedAt must not be null");
    }

    public Long getId() {
        return id;
    }

    public ShortUrl getShortUrl() {
        return shortUrl;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClickEvent that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
