# Requirements Specification

## Status

Approved. This document is the source of truth for scope. Implementation phases in `docs/engineering-summary.md` are derived from this specification, not the other way around.

## Source

This specification is derived from the given assignment brief (`Assignment AI-Proficient Software Engineer.pdf`), which is treated as the original stakeholder requirement for this project. The brief describes the scenario and evaluation criteria at a high level; this document resolves it into an implementable specification, with every open question either answered here as an explicit assumption or recorded as a decision in the decision log in `docs/engineering-summary.md`.

## 1. Problem statement

Build a URL shortener service that accepts a long URL, returns a short, unique code, redirects visitors from that code to the original URL, and records access analytics for that code.

## 2. In scope

- Short URL creation from a valid absolute HTTP/HTTPS URL.
- Redirect resolution from a short code to its original URL.
- Access analytics per short code (click count, first/last access).
- Input validation and consistent error responses.
- Environment-driven persistence configuration (local development vs. containerized deployment).

## 3. Out of scope for this iteration

- User authentication and per-user link ownership.
- Custom/vanity short codes.
- Link expiration policies (reserved for a later iteration; the data model must not block adding it).
- Rate limiting and abuse prevention (documented as a known risk, not implemented in this iteration).
- Multi-region or horizontally scaled analytics aggregation.

## 4. Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | The system must accept a URL and return a unique short code. |
| FR-2 | The system must reject syntactically invalid or disallowed URL schemes. |
| FR-3 | The system must redirect a valid short code to its original URL. |
| FR-4 | The system must return a `404` for an unknown or invalid short code. |
| FR-5 | The system must record an access event on every successful redirect. |
| FR-6 | The system must expose aggregate analytics for a given short code. |
| FR-7 | The system must return structured, consistent error responses for all failure cases. |

## 5. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Short code generation must be safe under concurrent requests (no duplicate codes assigned to different URLs). |
| NFR-2 | The service must run identically in a local JVM process and inside a container. |
| NFR-3 | Persistence must be swappable between an in-memory/dev datastore and a production-grade relational datastore without changing application code. |
| NFR-4 | The service must not log or expose sensitive request data (for example, full query strings with credentials) in error responses. |

## 6. Explicit assumptions

These are treated as decisions until superseded by a recorded change in the decision log in `docs/engineering-summary.md`.

- A1: Only absolute `http://` and `https://` URLs are accepted; scheme-relative and non-HTTP schemes (for example `javascript:`) are rejected.
- A2: Short codes are randomly generated, not derived from the URL content.
- A3: Resubmitting an already-shortened URL is handled per the policy recorded in `docs/engineering-summary.md` (ADR-001, duplicate handling).
- A4: Analytics granularity for this iteration is a running count plus first/last access timestamps; time-bucketed analytics are a future enhancement.

## 7. Acceptance criteria for the overall system

- A client can create a short URL, follow the redirect, and read back analytics reflecting that redirect, in a single end-to-end flow.
- All failure paths documented in the API specification return the documented status code and error shape.
- The same codebase runs against both the local dev datastore and the containerized production-like datastore, controlled only by configuration.
