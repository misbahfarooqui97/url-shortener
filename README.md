# URL Shortener

Spring Boot URL shortener built for the AI-assisted software engineering assignment, using a spec-driven development approach: requirements and API contracts are written and approved first, and implementation is scoped and validated against them.

## Documentation map

| Document | Purpose |
|---|---|
| [docs/specs/requirements-specification.md](docs/specs/requirements-specification.md) | Normalized, approved requirements — the source of truth for scope |
| [docs/specs/api-specification.md](docs/specs/api-specification.md) | Binding API contract implementation must conform to |
| [docs/architecture.md](docs/architecture.md) | Layered architecture, component responsibilities, and request flows |
| [docs/engineering-summary.md](docs/engineering-summary.md) | Delivery plan, decision log (4 ADRs), AI-assisted execution log, and risks/trade-offs |
| [docs/GREENFIELD-SCENARIO.md](docs/GREENFIELD-SCENARIO.md) | **Scenario 1:** Building the URL shortener from scratch (spec-driven, 82 tests, 7 phases) |
| [docs/BROWNFIELD-SCENARIO.md](docs/BROWNFIELD-SCENARIO.md) | **Scenario 2:** Improving an existing system (bug fix, performance, security hardening) |
| [docs/SECURITY-REVIEW.md](docs/SECURITY-REVIEW.md) | Security review: open-redirect and unsafe-scheme risk analysis |

## Prerequisites

- Java 21 or newer
- Maven 3.9+ or the included Maven wrapper
- Docker Desktop with Docker Compose for the containerized path

## Run locally

The default configuration uses an in-memory H2 database:

```powershell
.\mvnw.cmd spring-boot:run
```

The application will be available at `http://localhost:8080`.

## Run with Docker Compose (PostgreSQL)

Docker Compose starts the application alongside a real PostgreSQL 16 container, which is the production-realistic path. No local Postgres installation is required — Docker provisions and manages the database:

```powershell
docker compose up --build
```

The application will be available at `http://localhost:8080`, and health information is available at:

```text
http://localhost:8080/actuator/health
```

Stop the service with:

```powershell
docker compose down
```

Data persists across restarts in the `urlshortener-postgres-data` Docker volume. To reset the database, run `docker compose down -v`.

### Why H2 for local runs and PostgreSQL for Docker?

Both share the same JPA entities, repositories, and schema generation (`spring.jpa.hibernate.ddl-auto`), so the domain code is identical either way. Only the datasource connection details differ. This decision is recorded in [ADR-003](docs/engineering-summary.md#adr-003-persistence-strategy-across-environments).

| Path | Datastore | Purpose |
|---|---|---|
| `.\mvnw.cmd spring-boot:run` / plain JAR | H2 (in-memory, PostgreSQL compatibility mode) | Fastest possible local iteration and tests, zero external setup |
| `docker compose up` | Real PostgreSQL 16 | Production-realistic run, persistence across restarts, closer to real deployment behavior |

All datasource settings are environment-variable driven (see `src/main/resources/application.properties`), so switching between them requires no code changes — only environment configuration.

## API Quick Start

### Create a shortened link

```bash
curl -X POST http://localhost:8080/api/v1/short-urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/very/long/path/to/resource"}'
```

Response:
```json
{
  "code": "aB91xY1",
  "shortUrl": "http://localhost:8080/aB91xY1",
  "originalUrl": "https://example.com/very/long/path/to/resource",
  "createdAt": "2026-09-21T12:00:00Z"
}
```

### Follow the shortened link

```bash
curl -L http://localhost:8080/aB91xY1
```

Returns HTTP 302 redirect to the original URL.

### Get analytics

```bash
curl http://localhost:8080/api/v1/short-urls/aB91xY1/analytics
```

Response:
```json
{
  "code": "aB91xY1",
  "originalUrl": "https://example.com/very/long/path/to/resource",
  "totalClicks": 3,
  "firstAccessedAt": "2026-09-21T12:00:15Z",
  "lastAccessedAt": "2026-09-21T12:05:30Z"
}
```

## Validate

### Run all tests

```powershell
.\mvnw.cmd test
```

Expected result: **82 tests passing** (unit, integration, end-to-end, concurrency, security validation).

### Test coverage by area

- **Validators & normalization:** URL structure, scheme, case-handling, trailing slashes
- **Code generation:** Collision detection, uniqueness under load
- **Services:** Create, resolve, analytics, duplicate reuse (ADR-001), brownfield bug-fix
- **Controllers:** Request/response contracts, error handling (400, 404, 500)
- **Concurrency:** 50-thread unique-code test, 10-thread duplicate-submission test
- **Security:** Open-redirect mitigation, unsafe-scheme rejection, input size limits
- **Caching:** Cache hit/miss behavior, no negative caching, Caffeine eviction

## Build a deployable JAR

```powershell
.\mvnw.cmd clean package
```

Artifacts:
- `target/urlshortener-1.0-SNAPSHOT.jar` — executable JAR for deployment
- Runnable on any system with Java 21+: `java -jar target/urlshortener-1.0-SNAPSHOT.jar`

## Design decisions and trade-offs

See [docs/engineering-summary.md](docs/engineering-summary.md) for the complete decision log, including:

- **ADR-001:** Duplicate URL submission reuses an existing active code (not always a new one)
- **ADR-002:** Short codes are randomly generated and collision-detected (not hash-based)
- **ADR-003:** Persistence abstraction supports H2 (local) and PostgreSQL (production) without code changes
- **ADR-004:** Redirect lookups are cached in-memory (Caffeine) with 30-second TTL to keep latency low

## Assignment scenarios

The project demonstrates engineering judgment through three evaluation scenarios, all delivered in a single codebase:

1. **Greenfield scenario:** Build the core URL shortener capability from scratch, with full test coverage and decision documentation.
2. **Brownfield scenario:** Show realistic bug-finding and fixing: swapped timestamp lookups in analytics (caught by unit tests, fixed with regression coverage).
3. **Ambiguous-requirement scenario:** Decide what happens when a client submits the same URL twice (chose: reuse the existing code, per ADR-001).

All three scenarios are delivered end-to-end in this submission. See [docs/engineering-summary.md § 3](docs/engineering-summary.md#3-required-assignment-scenarios) for detailed scenario narratives and acceptance criteria.

## Current delivery status

**Complete.** The URL shortener is fully implemented and tested:
- ✅ All 4 functional requirements met (create, resolve, redirect, analytics)
- ✅ All 3 non-functional requirements met (latency via caching, concurrency safety, error handling)
- ✅ All 3 assignment scenarios documented and validated
- ✅ 82 tests passing (unit, integration, E2E, concurrency, security)
- ✅ Runs on plain JAR (H2) and Docker Compose (PostgreSQL)
- ✅ Security review completed (open-redirect and unsafe-scheme risks addressed)
