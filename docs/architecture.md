# Architecture Overview

This document describes how the URL shortener is structured and how a request moves
through it. It is written for a reviewer who has not read the code yet.

## 1. Layered design

The application follows a conventional layered architecture. Each layer only depends on
the layer below it; nothing below a layer knows about the layer above it (for example,
the service layer has no HTTP concepts — no status codes, no `@RequestMapping`).

```
┌─────────────────────────────────────────────────────────────┐
│  Web layer (Phase 4 — controllers, exception handling)      │
│  Translates HTTP requests/responses to and from the service │
│  layer. Owns status codes and the documented error shape.   │
└───────────────────────────┬───────────────────────────────────┘
                             │ calls
┌───────────────────────────▼───────────────────────────────────┐
│  Service layer (Phase 3 — business rules)                    │
│  ShortUrlService: create / resolve / analytics.               │
│  UrlValidator, UrlNormalizer, ShortCodeGenerator.              │
│  No HTTP knowledge. Fully unit-testable without Spring MVC.    │
└───────────────────────────┬───────────────────────────────────┘
                             │ calls
┌───────────────────────────▼───────────────────────────────────┐
│  Persistence layer (Phase 2 — domain and repositories)        │
│  ShortUrl, ClickEvent entities.                                │
│  ShortUrlRepository, ClickEventRepository (Spring Data JPA).   │
└───────────────────────────┬───────────────────────────────────┘
                             │ JPA / Hibernate
┌───────────────────────────▼───────────────────────────────────┐
│  Datastore (Phase 1 — environment-selected)                   │
│  H2 (local JAR, default) or PostgreSQL (Docker Compose).       │
└─────────────────────────────────────────────────────────────┘
```

Each layer was built and committed independently (Phase 1 → 2 → 3 → 4), and the
application was runnable and tested at the end of every phase — see
[docs/engineering-summary.md](engineering-summary.md) for the phase-by-phase plan and
acceptance criteria.

## 2. Components

### Persistence layer (`domain`, `repository`)

| Component | Responsibility |
|---|---|
| `ShortUrl` | One row per shortened link: `code` (unique), `originalUrl`, `normalizedUrl` (used for duplicate detection), `createdAt`, `active` flag. |
| `ClickEvent` | One row per successful redirect: which `ShortUrl` was hit and when (`accessedAt`). |
| `ShortUrlRepository` | Lookup by code, lookup by normalized URL restricted to active links, existence check for collision testing. |
| `ClickEventRepository` | Count of clicks for a `ShortUrl`, first/last access timestamp lookups. |

### Service layer (`service`)

| Component | Responsibility |
|---|---|
| `UrlValidator` | Rejects blank, over-length, malformed, or non-http(s) URLs (FR-2). |
| `UrlNormalizer` | Produces the canonical form of a URL used to detect duplicates (lower-cased host/scheme, default port removed, trailing slash normalized, fragment dropped, query preserved). Backs ADR-001. |
| `ShortCodeGenerator` / `RandomShortCodeGenerator` | Generates a random, fixed-length alphanumeric code. Never derived from the URL (ADR-002). |
| `CachedShortUrlLookup` | Caches the code → `ShortUrl` lookup used by resolve/analytics with Caffeine (ADR-004). A separate bean from `ShortUrlService` so Spring's `@Cacheable` proxy is actually invoked (calling a `@Cacheable` method from within the same class bypasses the proxy). Cache misses are never cached, so a newly created code is visible immediately. |
| `ShortUrlService` | Orchestrates the above: validate → normalize → reuse-or-create (ADR-001) → generate-with-retry (ADR-002) on create; look up (via `CachedShortUrlLookup`) + record a click on resolve; aggregate click data on analytics. This is the single entry point the web layer will call. |
| `ClockConfig` | Supplies an injectable `Clock` bean so timestamps can be fixed in unit tests instead of depending on `Instant.now()` directly. |
| `CacheConfig` | Enables Spring's caching support (`@EnableCaching`); the actual `CacheManager` (Caffeine) is auto-configured from `spring.cache.*` properties. |

### Exceptions (`exception`)

| Exception | Meaning | Maps to |
|---|---|---|
| `InvalidUrlException` | Submitted URL failed validation (FR-2). | `400` |
| `ShortUrlNotFoundException` | Code does not resolve to an active link (FR-4). | `404` |
| `ShortCodeGenerationException` | Collision retry budget exhausted (ADR-002). | `500` |

These are plain `RuntimeException`s with safe, user-facing messages. The web layer
(Phase 4) will translate them into the documented error response shape — this keeps
HTTP-status decisions out of the service layer.

### Web layer (Phase 4 — not yet implemented)

Will expose the three endpoints from
[docs/specs/api-specification.md](specs/api-specification.md):

- `POST /api/v1/short-urls` → `ShortUrlService.createShortUrl(...)`
- `GET /{code}` → `ShortUrlService.resolve(...)`, then a `302` redirect
- `GET /api/v1/short-urls/{code}/analytics` → `ShortUrlService.getAnalytics(...)`

plus a global exception handler mapping the exceptions above to the documented error
shape.

## 3. Request flows

### Create a short URL

```
Client → POST /api/v1/short-urls {"url": "..."}
       → ShortUrlService.createShortUrl(url)
           → UrlValidator.validate(url)                    (throws InvalidUrlException on failure)
           → UrlNormalizer.normalize(url)
           → ShortUrlRepository.findByNormalizedUrlAndActiveTrue(normalized)
               → found  → return existing ShortUrl (ADR-001 reuse)
               → absent → generate a code (retry on collision, ADR-002)
                        → ShortUrlRepository.save(new ShortUrl(...))
       ← 201 Created {"code", "shortUrl", "originalUrl", "createdAt"}
```

### Redirect

```
Client → GET /{code}
       → ShortUrlService.resolve(code)
           → ShortUrlRepository.findByCode(code)           (throws ShortUrlNotFoundException on miss)
           → ClickEventRepository.save(new ClickEvent(shortUrl, now))   (FR-5)
       ← 302 Found, Location: <originalUrl>
```

### Analytics

```
Client → GET /api/v1/short-urls/{code}/analytics
       → ShortUrlService.getAnalytics(code)
           → ShortUrlRepository.findByCode(code)           (throws ShortUrlNotFoundException on miss)
           → ClickEventRepository.countByShortUrl(shortUrl)
           → ClickEventRepository.findFirstByShortUrlOrderByAccessedAtAsc/Desc(shortUrl)
       ← 200 OK {"code", "originalUrl", "totalClicks", "firstAccessedAt", "lastAccessedAt"}
```

## 4. Cross-cutting decisions

These are recorded in full, with rationale and consequences, in the decision log in
[docs/engineering-summary.md](engineering-summary.md#2-decision-log):

- **ADR-001** — Resubmitting an already-shortened URL reuses the existing active code
  instead of minting a new one.
- **ADR-002** — Short codes are randomly generated with a bounded collision retry, not
  derived from the URL.
- **ADR-003** — The same JPA entities and repositories run against H2 locally and
  PostgreSQL in Docker; only datasource configuration differs by environment.

## 5. Why this shape

- **Service layer has no HTTP or persistence-framework leakage.** `ShortUrlService` can be
  fully unit tested with mocked repositories (see
  `src/test/java/.../service/ShortUrlServiceTest.java`) without starting Spring MVC or a
  database, which keeps the core business rules fast to test and easy to reason about.
- **Validation and normalization are separate, single-purpose classes**, not embedded in
  the service method, so each has focused unit tests and can be reused independently
  (for example, normalization is also useful for future admin/reporting tooling).
- **Repository interfaces expose only the queries the service layer actually needs**
  (`findByCode`, `findByNormalizedUrlAndActiveTrue`, `existsByCode`, click aggregates)
  rather than a generic query surface, keeping the persistence contract intentional and
  easy to review.
