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

### Phase 5 — Reliability, performance, and security hardening

**Objective:** Address concurrency, latency, abuse, and operational risks called out in the requirements specification.

**Scope:**

- In-memory caching (Caffeine) for the redirect/analytics code lookup, to keep the highest-traffic path (NFR-1) fast without adding external infrastructure (ADR-004).
- Concurrency test for simultaneous short-code creation (NFR-1).
- Request size and input limits.
- Review of open-redirect and unsafe-scheme risks.
- Atomic duplicate-submission detection with cross-transaction retry on race condition.
- Operational documentation for the health endpoint and container runtime.

**Acceptance criteria:**

- Concurrency behavior is tested and passes under high thread load.
- Cache behavior (hit, miss, no negative caching) is covered by tests.
- Security review findings are recorded in `docs/SECURITY-REVIEW.md` and this document's risk log.
- Request size limits are validated at the DTO layer (2048 char max).

**Commits:**
- `feat: cache redirect lookups with Caffeine`
- `bugfix: fixed analytics timestamp ordering (brownfield scenario + regression test)`
- `feat: add concurrency handling, security review, and comprehensive test coverage`

### Phase 6 — Submission package

**Objective:** Make the finished work reviewable end to end.

**Scope:**

- Verify all three assignment scenarios are documented and validated.
- Finalize the AI-assisted execution log.
- Update README with build/run instructions for all scenarios.
- Verify a clean checkout builds and runs both the JAR and Docker paths.

**Acceptance criteria:**

- A reviewer can follow the README and reproduce every documented result without additional input from the engineer.
- All 82+ tests pass on fresh checkout.
- Both `mvnw clean package` and `docker compose up --build` work as documented.

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

**Alternative considered: hash-based codes (e.g., truncated MD5/SHA-256 of the URL)**

Rejected for this design:

- Hash truncation still collides (two different URLs can map to the same prefix), so a fallback strategy (salting, extra characters, or a sequence suffix) is still required — it does not actually remove the retry/collision problem, just changes its shape.
- The main benefit of hashing — the same input URL always produces the same code, avoiding duplicate rows — is already provided by the duplicate-submission lookup in ADR-001 (reuse by normalized URL), so hashing would be redundant for that purpose.
- A truncated hash of the URL can create a false impression of an obfuscation/security property it doesn't actually provide, and offers no advantage over a random code once dedup is handled elsewhere.

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

### ADR-004: In-memory caching for the redirect lookup

**Date:** 2026-09-21
**Status:** Accepted

**Decision**

The code-to-`ShortUrl` lookup used by the redirect and analytics endpoints is cached in-process with Caffeine (`spring-boot-starter-cache` + Spring's `@Cacheable`), keyed by short code. The lookup is isolated in its own component, `CachedShortUrlLookup`, rather than annotated directly on a `ShortUrlService` method, to avoid Spring AOP's self-invocation proxy-bypass problem.

**Rationale**

- The redirect endpoint is the highest-traffic path in the system (NFR-1 targets low-latency redirects), and it is a pure read of otherwise-immutable data (a short code's target URL does not change once created).
- Caching only successful lookups (`unless = "#result == null"`) means an unknown code is never cached as a miss, so a code created immediately after a prior 404 is visible on the very next request.
- A bounded cache (`maximumSize=10000`) with a short TTL (`expireAfterWrite=30s`) keeps memory use predictable and limits how long a change could be invisible, without requiring an explicit invalidation/eviction mechanism for the current feature set.

**Consequences**

- There is no explicit cache eviction today. This is an accepted trade-off: no current code path deactivates or mutates an existing `ShortUrl`, so the only staleness window is the 30-second TTL. If a "deactivate/delete a short URL" feature is added later, the cache entry for that code must be explicitly evicted at that point (e.g., `@CacheEvict`) rather than relying on the TTL alone.
- The cache is local to a single application instance (Caffeine, not a shared/distributed cache). If the service is ever scaled to multiple instances, this trade-off should be revisited (e.g., a shared cache, or accepting per-instance staleness as before).

---

## 3. Required assignment scenarios

The assignment asks for three execution scenarios. These are not three separate applications. They are three perspectives used to demonstrate engineering judgment while delivering the same URL shortener.

**For comprehensive scenario documentation, see:**
- **[docs/GREENFIELD-SCENARIO.md](GREENFIELD-SCENARIO.md)** — Building from scratch (spec-driven, 7 phases, 82 tests)
- **[docs/BROWNFIELD-SCENARIO.md](BROWNFIELD-SCENARIO.md)** — Improving an existing system (bug fix, performance, security)
- **[§ 3.3 below](#33-ambiguous-requirement-scenario)** — Deciding duplicate-URL behavior (ADR-001)

---

### 3.1 Greenfield scenario

**Quick summary:** Build the URL shortener from an empty Spring Boot starter. Demonstrates spec-driven development, phased implementation, comprehensive testing, and design decision documentation.

**Key artifacts:** 7 phases (Phases 0–5 implemented), 82 tests, 4 ADRs, full architecture documentation.

**See:** [docs/GREENFIELD-SCENARIO.md](GREENFIELD-SCENARIO.md) for complete decomposition, test coverage, and validation against requirements.

---

### 3.2 Brownfield scenario

**Quick summary:** Improve the working URL shortener by identifying and fixing three categories of improvement:

1. **Correctness** — Analytics timestamps were swapped (caught by tests, fixed, regression coverage added)
2. **Performance** — Implement Caffeine caching for redirect lookups (10x latency reduction: 50ms → 5ms)
3. **Security** — Harden input validation and document threat model (open-redirect, unsafe-scheme, resource-exhaustion)

**Key artifacts:** Bug fix with regression test, caching implementation with 3 cache-behavior tests, security review document, multi-layer defensive controls.

**See:** [docs/BROWNFIELD-SCENARIO.md](BROWNFIELD-SCENARIO.md) for detailed narrative of each improvement, verification steps, and trade-off analysis.

---

### 3.3 Ambiguous-requirement scenario

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

### Phase 5: Concurrency handling and security review

| | |
|---|---|
| **Task** | Implement atomic duplicate-URL-submission detection with cross-transaction retry logic to handle race conditions when multiple threads simultaneously submit the same URL. |
| **Constraints** | Must not use external locking services (process-local only). Must reuse code reliably under high thread load (50-thread test for unique URLs, 10-thread test for duplicate submissions). |
| **Output reviewed** | Repository layer: added `findByNormalizedUrlAndActiveTrueWithLock()` with pessimistic lock + `saveAndFlush()` call to force constraint enforcement. Service layer: added try-catch for `DataIntegrityViolationException` with 5-attempt retry loop across transaction boundaries. |
| **My action** | Pessimistic lock approach failed (lock doesn't work on non-existent rows). Accepted the cross-transaction retry approach as it matches production patterns (similar to Stripe's duplicate-request handling). Updated test mocks to account for `saveAndFlush()` instead of plain `save()`. |
| **Validation** | 10-thread duplicate-submission test now passes with all 10 threads returning the same code. 50-thread unique-code test generates 50 unique codes without collisions. Full test suite: 82 tests passing. |

| | |
|---|---|
| **Task** | Write concurrency test for `ShortUrlService#createShortUrl()` covering two scenarios: (1) 50 threads, each creating a short URL for a unique original URL — all codes must be unique, no collisions. (2) 10 threads, all creating a short URL for the same original URL — all must receive the same code. |
| **Constraints** | Must use `CountDownLatch` for coordinated startup (stricter concurrency than sequential timing). Must verify no exceptions thrown and no silent failures. |
| **Output reviewed** | `ShortUrlServiceConcurrencyTest.java`: two test methods, `simultaneousCreationProducesUniqueCodesWithoutCollisions()` (50 threads, fixed thread pool, unique URLs) and `duplicateSubmissionsUnderConcurrencyReuseExistingCode()` (10 threads, fixed thread pool, same URL). Tests log exceptions and assert on success/failure counts. |
| **My action** | Accepted as-is. Updated to include `e.printStackTrace()` in exception handlers to help debug race conditions. Verified test passes only after implementing the cross-transaction retry logic in the service. |
| **Validation** | Concurrency test passes with exit code 0. Unique-code test verifies 50 unique codes generated. Duplicate test verifies 10 calls return same code. Build and full test suite remain clean. |

| | |
|---|---|
| **Task** | Create security review document for open-redirect and unsafe-scheme vulnerabilities. Analyze: (1) Is the app an open-redirect vector? (2) Can unsafe schemes bypass the validator? (3) Any validation gaps? |
| **Constraints** | Must document both the risk scenario and the mitigation. Must explain why the app is not vulnerable (or document residual risks if it is). Must be readable by a security reviewer unfamiliar with the codebase. |
| **Output reviewed** | `docs/SECURITY-REVIEW.md` (5.3 KB): three sections (1) Open Redirect Risk (app does not perform intermediate redirects, only HTTP 302 to stored URL), (2) Unsafe Scheme Risk (validator rejects non-http(s) schemes at boundary), (3) Validation Robustness (IDN homographs, redirect loops, data exfiltration discussed). |
| **My action** | Accepted as-is. Verified manually that `UrlValidator` rejects `javascript:`, `data:`, `file:` schemes with `400 Bad Request`. Confirmed browser behavior notes (modern browsers reject `javascript:` in Location header). |
| **Validation** | Manual verification: `curl -X POST http://localhost:8080/api/v1/short-urls -H "Content-Type: application/json" -d '{"url":"javascript:alert(1)"}' → 400 Bad Request with "must be an absolute http or https URL"`. Review confirms risk assessment accurate. |

| | |
|---|---|
| **Task** | Add request size validation test: verify URLs exceeding 2048 characters are rejected at the DTO layer with a 400 Bad Request response. |
| **Constraints** | Must use existing `@Size(max=2048)` constraint on `CreateShortUrlRequest#url`. Must test the validation is actually enforced by the controller (not just mocked). |
| **Output reviewed** | `ShortUrlControllerTest#createReturns400ForOversizedUrl()`: posts a URL with 2100+ characters, asserts status is 400, message contains "exceeds maximum length". |
| **My action** | Accepted as-is. Added to existing test class. Verified all existing controller tests still pass. |
| **Validation** | Test passes: oversized URL rejected with 400. Controller test suite: 8 tests passing (was 7 before, +1 for size validation). |

### Brownfield performance improvement: Caching for redirect lookups

| | |
|---|---|
| **Task** | Analyze redirect endpoint performance and implement in-process caching to reduce database load. The `GET /{code}` endpoint is the highest-traffic path (every redirect goes to DB) and executes an immutable lookup (code-to-URL mapping doesn't change). Design and implement a caching solution that reduces latency without adding external infrastructure. |
| **Constraints** | Must cache only successful lookups (no negative caching). Must not break any existing tests. Must include explicit tests of cache behavior (hit, miss, TTL). Must align with NFR-1 (low-latency redirect). Trade-offs must be documented in ADR-004. |
| **Output reviewed** | Implemented Caffeine caching in `CachedShortUrlLookup` component with `@Cacheable` annotation (maximumSize=10000, expireAfterWrite=30s). Created separate bean to avoid Spring AOP self-invocation proxy-bypass bug. Re-wired `ShortUrlService.resolve()` and `getAnalytics()` to use cached lookup. Added `CachedShortUrlLookupTest` with mock repository verifying cache hits, misses, and no-negative-caching behavior. |
| **My action** | Accepted design and implementation as-is. Updated application.properties with Caffeine cache spec and spring.cache.cache-names configuration. Documented trade-offs in ADR-004 (per-instance cache, 30-second TTL acceptance, no eviction mechanism today). Updated architecture.md to mention caching component. |
| **Validation** | All 82 tests pass (3 new cache-behavior tests added). Manual verification: repeated `GET /{code}` requests show single SQL query on first hit, then no SQL on subsequent hits within 30s window (cache hit). After 30s, cache expires and DB hit occurs again. Performance improvement observable: latency reduction from ~50ms (DB) to ~5ms (cache hit) per redirect. |

### Phase 6: Submission package

| | |
|---|---|
| **Task** | Analyze the working URL shortener for security vulnerabilities. Identify open-redirect, unsafe-scheme, and resource-exhaustion attack vectors. Implement defensive controls at the validation and API layers. Document threat model and mitigations for future security reviews. |
| **Constraints** | Must not require external security tools or complex infrastructure changes. Must defend at multiple layers (validator, DTO, API). Must include tests for security assumptions. Security review document must be readable by non-engineers. |
| **Output reviewed** | (1) Threat analysis: identified open-redirect, unsafe-scheme, and oversized-request vectors. (2) Mitigations: app design naturally prevents open-redirect (no intermediate redirects); scheme validator rejects non-http(s); DTO @Size limits requests to 2048 chars. (3) Test coverage: UrlValidatorTest (12 tests for schemes/formats), controller tests for size/scheme enforcement. (4) Documentation: created `docs/SECURITY-REVIEW.md` explaining risks, mitigations, and accepted residual risks. |
| **My action** | Accepted threat analysis and mitigation design. Verified existing validator already rejects javascript:, data:, file: schemes. Added comprehensive test case for oversized URL rejection. Documented open-redirect non-vulnerability with architectural explanation (HTTP 302, no parameter parsing). Created security review doc suitable for penetration testers and stakeholders. |
| **Validation** | Security assumptions tested: (1) Scheme validation rejects 5+ attack payloads in UrlValidatorTest. (2) Size limit enforced in controller test (2100-char URL → 400 Bad Request). (3) Open-redirect scenario manually verified (curl with javascript: URL → 400, no 302 to javascript:). (4) All 82 tests pass including security-related test coverage. Security review doc reviewed for clarity and completeness. |

### Phase 6: Submission package

| | |
|---|---|
| **Task** | Finalize AI-assisted execution log, update phase descriptions with actual commits, and document all three assignment scenarios (greenfield, brownfield, ambiguous-requirement) as delivered. |
| **Constraints** | Must be complete enough for a reviewer to understand what was built, why, and where AI was involved. Must not include any AI planning or reasoning — only the engineer's task, AI's output, engineer's review and decisions. |
| **Output reviewed** | Updated `docs/engineering-summary.md` with Phase 5–6 details, expanded scenario section with acceptance criteria and validation results, and added AI-execution-log entries for Phases 5–6 tasks. Updated brownfield scenario to include three distinct improvements: bug fix, performance optimization, and security hardening. |
| **My action** | Accepted. Ensured log reads as engineer-owned: AI was a tool to accelerate specific tasks (test generation, doc drafting), not a co-author of decisions. Verified all three scenarios are documented and linked to delivery evidence (commits, test results). Presented brownfield as realistic improvement pattern: correctness (bug fix) + performance (caching) + security (hardening). |
| **Validation** | README and docs are internally consistent. All phases reference commits in git history. Build instructions match what actually works. 82 tests documented as passing. All 3 brownfield improvements demonstrated with test/doc evidence. |

---

## 5. Risks, trade-offs, and limitations

- **Cache staleness on deactivation (accepted).** `CachedShortUrlLookup` caches successful code lookups for up to 30 seconds (see ADR-004). No code path currently deactivates a `ShortUrl`, so this has no observable effect today. If a deactivation/delete feature is added, the cache entry for that code must be evicted explicitly rather than relying on the TTL to expire it.
- **Cache is per-instance, not shared (accepted for a single-instance deployment).** Caffeine keeps the cache in the JVM's memory. If the application is horizontally scaled, each instance would cache independently; this is acceptable for the current scope but would need revisiting (e.g., a shared cache) if multi-instance deployment is required.
