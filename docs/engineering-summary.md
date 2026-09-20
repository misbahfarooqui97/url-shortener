# Engineering Summary

This document consolidates the delivery plan, architecture/product decisions, required assignment scenarios, and AI-assisted execution traceability for the URL shortener project. It is built spec-first against `docs/specs/requirements-specification.md` and `docs/specs/api-specification.md`.

---

## 1. Delivery plan

### Approach

This project follows spec-driven development: the specifications in `docs/specs/` are written and approved first, and all implementation work is scoped against them. Phases below are my own breakdown of the approved specification into buildable increments; each phase leaves the application in a runnable, tested state before I move to the next one.

AI tools are used within individual tasks (implementation drafts, test generation, debugging assistance, documentation drafting) once I have defined the task's intent, constraints, and acceptance criteria. I review, edit, or reject every AI-generated change before it is committed. See [Section 4](#4-ai-assisted-execution-log) for the task-level record.

### Phase 0 — Requirement gathering and specification

**Objective:** Turn the given assignment brief into an unambiguous, approved specification before any code is written.

**Input:** The assignment document (`Assignment AI-Proficient Software Engineer.pdf`) is the source requirement for this project. It describes the scenario, scope, and evaluation criteria at a high level and intentionally leaves several product decisions open.

**Scope:**

- Read and interpret the assignment brief; separate explicit requirements from open/ambiguous points.
- Normalize the brief into `docs/specs/requirements-specification.md`: functional requirements, non-functional requirements, in-scope/out-of-scope boundaries, and explicit assumptions.
- Define the binding contract in `docs/specs/api-specification.md` before any controller or service code exists.
- Record decisions for every ambiguous point identified in the brief in [Section 2](#2-decision-log), with rationale and consequences.

**Acceptance criteria:**

- Every functional and non-functional requirement in the specification traces back to a line in the assignment brief or to an explicitly recorded assumption.
- No implementation-level ambiguity remains: any question a developer could reasonably ask while coding is already answered in the specification or decision log.
- The specification is reviewed and approved before Phase 1 begins.

**Commit:** `docs: capture requirements and API specification from assignment brief`

### Phase 1 — Foundation

**Objective:** Make the starter project reproducible and runnable before any domain code is added, per the approved specification.

**Scope:**

- Add Spring Web, Validation, JPA, PostgreSQL, H2, and Actuator dependencies.
- Add environment-driven datasource configuration (H2 default, PostgreSQL via Docker Compose), per ADR-003.
- Add a Dockerfile and Docker Compose stack (application + PostgreSQL).

**Acceptance criteria:**

- `mvnw clean package` builds and the context-load test passes.
- `docker compose up --build` starts the application against a real PostgreSQL container and `/actuator/health` reports `UP`.

**Commit:** `chore: establish runnable project foundation`

### Phase 2 — Domain and persistence

**Objective:** Implement the data model backing the API specification.

**Scope:**

- Short URL entity and repository (code, original URL, created timestamp, active flag).
- Access/click event entity and repository.
- Repository-level tests against the configured datastore.

**Acceptance criteria:**

- Short codes are enforced unique at the persistence layer.
- Entities and repositories satisfy FR-1, FR-3, FR-5 from the requirements specification.

**Commit:** `feat: add short URL persistence model`

### Phase 3 — Core application services

**Objective:** Implement business rules independent of the HTTP layer.

**Scope:**

- URL validation per FR-2 and the accepted-scheme rule (A1).
- Short code generation and bounded collision retry (ADR-002).
- Duplicate-submission handling (ADR-001).
- Create, resolve, and analytics service methods.

**Acceptance criteria:**

- Unit tests cover valid, invalid, duplicate, and collision cases.
- Service behavior matches the API specification without depending on any controller.

**Commit:** `feat: implement URL shortener business services`

### Phase 4 — REST API

**Objective:** Expose the services as the contract defined in `docs/specs/api-specification.md`.

**Scope:**

- Create, redirect, and analytics controllers.
- Global exception handling producing the documented error shape.
- Controller and end-to-end tests (create → redirect → analytics).

**Acceptance criteria:**

- Every endpoint and failure case in the API specification is implemented and tested.
- No internal exception detail leaks through an error response (NFR-4).

**Commit:** `feat: expose URL shortener REST APIs`

### Phase 5 — Reliability and security hardening

**Objective:** Address concurrency, abuse, and operational risks called out in the requirements specification.

**Scope:**

- Concurrency test for simultaneous short-code creation (NFR-1).
- Request size and input limits.
- Review of open-redirect and unsafe-scheme risks.
- Operational documentation for the health endpoint and container runtime.

**Acceptance criteria:**

- Concurrency behavior is tested or explicitly bounded and documented.
- Security review findings are recorded in this document's risk log.

**Commit:** `hardening: improve reliability and security controls`

### Phase 6 — Submission package

**Objective:** Make the finished work reviewable end to end.

**Scope:**

- Complete the greenfield, brownfield, and ambiguous-requirement scenario write-ups.
- Finalize the AI-assisted execution log.
- Verify a clean checkout builds and runs both the JAR and Docker paths from the committed instructions alone.

**Acceptance criteria:**

- A reviewer can follow the README and reproduce every documented result without additional input from me.

**Commit:** `docs: complete engineering submission package`

---

## 2. Decision log

Architecture and product decisions for this project, recorded as they are made. Each entry is final until superseded by a later entry that explicitly says so.

### ADR-001: Duplicate URL submission behavior

**Date:** 2026-09-20
**Status:** Accepted

**Context**

The requirement does not state what should happen when a client submits a URL that has already been shortened. This is the ambiguous-requirement case called out in the assignment scope.

**Options considered**

1. Always issue a new code for every request, even for a URL that already has one.
2. Return the existing active code for an identical, normalized URL.
3. Make the behavior configurable per client.

**Decision**

Option 2: reuse an existing active short code for an identical, normalized URL. New codes are issued only for URLs that have not been shortened before, or whose prior code is no longer active.

**Rationale**

- Avoids duplicate rows and fragmented analytics for the same destination.
- Matches the behavior of common public URL shorteners, which is the least surprising default.
- Keeps the response for repeated input deterministic, which simplifies testing.

**Consequences**

- URL normalization rules (trimming, case handling for host, trailing slash handling) must be defined and covered by tests.
- The API specification's success response for `POST /api/v1/short-urls` is the same whether the code is newly created or reused; the response does not currently distinguish the two cases. This is accepted for this iteration and can be revisited if clients need to distinguish "created" from "reused."

### ADR-002: Short code generation strategy

**Date:** 2026-09-20
**Status:** Accepted

**Decision**

Short codes are randomly generated (not derived from a hash or sequence of the URL) and checked for collisions against existing codes before being persisted, with a bounded number of retries.

**Rationale**

- Avoids predictable/guessable codes.
- Decouples code format from URL content, so the same normalized URL will still reuse its own code (ADR-001) without needing to recompute a hash on every lookup.

**Consequences**

- Collision handling must be tested under the maximum retry bound; exhausting retries must fail with a clear, non-leaking error rather than looping indefinitely.

### ADR-003: Persistence strategy across environments

**Date:** 2026-09-20
**Status:** Accepted

**Decision**

The application uses JPA against an environment-selected datasource: H2 (in-memory, PostgreSQL compatibility mode) for local runs and the plain executable JAR, and a real PostgreSQL container when run through Docker Compose. Domain code, entities, and repositories are identical in both cases; only datasource connection properties differ.

**Rationale**

- The plain executable JAR deliverable must run with zero external setup.
- The Docker deliverable should be representative of a real production datastore, not just a compatibility-mode substitute.
- Keeping both behind the same JPA abstraction avoids maintaining two persistence implementations.

**Consequences**

- Integration tests that assert Postgres-specific behavior (if any are added later) must run against Testcontainers-managed Postgres, not H2, since H2's compatibility mode does not cover every Postgres-specific feature.

---

## 3. Required assignment scenarios

The assignment asks for three execution scenarios. These are not three separate applications. They are three perspectives used to demonstrate engineering judgment while delivering the same URL shortener.

### Greenfield scenario

**Scenario statement:** Build the URL shortener's core capability from an empty Spring Boot starter: create a short link, redirect through its code, and expose basic analytics.

**Decomposition:**

1. Normalize the requirement and document assumptions.
2. Define the API contract and status codes.
3. Select persistence and code-generation strategies.
4. Implement the domain model and repositories.
5. Implement URL validation and short-code generation.
6. Implement create, resolve, redirect, and analytics services.
7. Expose REST endpoints and consistent errors.
8. Add unit, integration, and end-to-end tests.
9. Review security, reliability, and operational risks.

**Validation:**

- A valid URL produces a short code.
- The code redirects to the original URL.
- The redirect increments analytics.
- Invalid, unknown, and expired links behave as documented.

### Brownfield scenario

**Scenario statement:** Improve the supplied Spring Boot starter without discarding its existing structure or startup behavior.

**Existing baseline:**

- Java 17 Spring Boot application.
- Maven wrapper and standard source layout.
- Application bootstrap class.
- Context-load test.
- No domain, persistence, API, or container runtime yet.

**Brownfield decisions:**

- Preserve the existing package root and application class.
- Preserve and extend the existing context-load test.
- Add capabilities incrementally rather than replacing the project.
- Keep configuration environment-driven so local H2 and Docker PostgreSQL can coexist.
- Validate each phase against the original baseline.

**Validation:**

- Existing application startup remains valid.
- Existing test continues to pass.
- New modules are isolated by responsibility.
- Docker startup uses the same application artifact as local execution.

### Ambiguous-requirement scenario

**Scenario statement:** Decide what should happen when a client submits the same original URL more than once.

**Options:**

| Option | Benefit | Cost |
|---|---|---|
| Always create a new code | Simple semantics; each request is independent | Duplicate records and fragmented analytics |
| Reuse an active code | Avoids duplicates and provides a stable link | Requires URL lookup and a clear normalization rule |
| Make behavior configurable | Supports multiple products | More complexity than the prototype needs |

**Decision:** See ADR-001. Reuse an existing active short URL for an identical normalized URL.

**Questions that had to be resolved before implementation:**

- Is URL normalization limited to trimming whitespace, or does it include host and path normalization?
- Should expired links be reusable?
- Should query-string ordering be preserved exactly?
- Does the product need a way to explicitly request a new code?

**Validation:**

- Repeating the same supported URL returns the documented result.
- Different URLs never reuse the same code.
- Expired or inactive links follow the documented policy.

---

## 4. AI-assisted execution log

This log records where AI tools were used to accelerate execution once a task's intent, constraints, and acceptance criteria had already been defined. It exists to satisfy the assignment's traceability requirement: AI assists within a task; I define the task, review the output, and own the result.

Entries are added as tasks are completed. Each entry states what was asked for, what was produced, and what I changed, accepted, or rejected.

**Log format:**

| Field | Meaning |
|---|---|
| Task | The task I defined, tied to a phase above |
| Constraints given to the AI tool | What I told it not to do, or what it had to conform to (a spec, an existing class, a test) |
| Output reviewed | Summary of what came back |
| My action | Accepted as-is / edited / rejected, with reason |
| Validation | How I confirmed the result was correct (test run, manual check, build) |

**Entries:**

<!-- Add entries below as each phase is executed. -->

---

## 5. Risks, trade-offs, and limitations

<!-- Populated as phases 5 and 6 are executed: known risks, accepted trade-offs, and explicit limitations of the delivered prototype. -->
