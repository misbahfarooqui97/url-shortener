# URL Shortener

Spring Boot URL shortener built for the AI-assisted software engineering assignment, using a spec-driven development approach: requirements and API contracts are written and approved first, and implementation is scoped and validated against them.

## Documentation map

| Document | Purpose |
|---|---|
| [docs/specs/requirements-specification.md](docs/specs/requirements-specification.md) | Normalized, approved requirements — the source of truth for scope |
| [docs/specs/api-specification.md](docs/specs/api-specification.md) | Binding API contract implementation must conform to |
| [docs/engineering-summary.md](docs/engineering-summary.md) | Delivery plan, decision log, required scenarios, AI-assisted execution log, and risks/trade-offs |

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

## Validate

```powershell
.\mvnw.cmd test
```

## Current delivery status

The application is being delivered in the phases documented in [docs/engineering-summary.md](docs/engineering-summary.md), each committed only once its acceptance criteria pass. Domain and API implementation for the core URL shortener capability follows in the next phases.
