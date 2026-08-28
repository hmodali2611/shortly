# Testing Approach

## Automated Gates

`docker compose --profile test run --rm tests` is the reproducible Docker-only gate. It runs JUnit 5 unit tests, the Testcontainers integration tests, Spotless, SpotBugs, and JaCoCo. JaCoCo enforces at least 80% aggregate line coverage over the critical management, security, redirect, and analytics services. A host-side `./mvnw verify` requires Java 25 and a Testcontainers-compatible Docker socket; Docker-backed tests fail rather than silently skip when that socket is unavailable.

Latest local run:

- Build: passed
- Tests: 49 run, 0 failures, 0 errors, 0 skipped in the Docker-backed run
- Static analysis: 0 SpotBugs warnings/errors
- Formatting: 46 Java files clean
- Critical-service line coverage: 95.8% (160/167), independently measured from the JaCoCo report

The tests cover URL safety, anonymous metadata access, aliases, generated-code collision retry, create/metadata/delete behavior, cache failure fallback, database-backed expiry, expiry-bounded cache TTL, queue saturation, and stats aggregation. The Testcontainers integration test exercises create, redirect, metadata, deletion, and an expired PostgreSQL row returning `410` on a cache miss; stats and dependency health were exercised separately during the documented runtime smoke checks.

`RedirectControllerHotPathTest` closes two properties that were previously manual observation only. `redirectStaysFastWhileClickFlushIsStalledOnTheDatabase` (T-11) blocks a mocked `ClickFlusher` batch write mid-flight and asserts a sequence of 50 redirects still returns in under two seconds while a full queue drops rather than blocks — proving NFR-4 rather than inferring it from the bounded-queue implementation. `neverLogsRawClientAddressOrTargetUrl` (T-22/NFR-6) attaches a Logback `ListAppender` to the root logger around a redirect carrying a marked target URL and a synthetic client address, then asserts neither value appears in any captured log message or MDC context. Both run without Docker.

Three more previously "planned" acceptance criteria are now automated. `RateLimiterTest` (T-9, NFR-2) mocks the Redis counter to assert `429` over the configured limit and `503` when Redis itself fails. `AliasValidatorTest` (T-14) asserts a 32-character alias round-trips through the validator and a 33-character one is rejected — the boundary the schema's `VARCHAR(32)` column depends on, and the same boundary a past AI-generated migration got wrong (`EXECUTION-LOG.md` ledger #3). This closes the validator half of T-14 only; a database-level round-trip proving the schema column itself holds 32 characters remains unautomated. `GlobalExceptionHandlerTest` (T-23) asserts the RFC 7807 envelope shape — `status`, `title`, `detail`, `type`, `instance` — for a representative `404` and `503`.

The Flyway migration test applies V1 and V2, inserts representative link and click data, then applies V3 and verifies that both records survive while the obsolete ownership column and index are removed.

## Runtime and Security Evidence

Docker Compose startup, health/readiness, create/metadata/redirect/stats/delete, and post-delete `410` behavior passed locally. The k6 creation/read workload created 100 unique URLs and completed exactly 10,000 redirect reads with 0% failures and 32.11 ms p95. PostgreSQL contained all 10,000 analytics events across all 100 links after asynchronous flushing.

Clean-source validation copied the intended submission files into an isolated directory while excluding `.git`, `target`, `.env`, editor files, and logs. `docker compose up --build -d --wait` built from a 775 KB context, all services became healthy, readiness returned `UP`, and a create-plus-redirect smoke flow returned `201` then `302`. A fresh clone from GitHub subsequently reproduced the current 49-test gate with zero failures or skips, 46 Java files Spotless-clean, no SpotBugs findings, JaCoCo enforcement passing, successful packaging, and `BUILD SUCCESS`. The earlier committed 39-test baseline and 41-test hot-path follow-up remain historical milestones in `EXECUTION-LOG.md`.

The analytics flusher now processes up to 20 batches of 500 events per scheduled invocation, matching the default 10,000-event queue capacity. Tests verify both multi-batch draining and event restoration after a failed database write.

Two additional low-risk optimizations passed the full gate. `.dockerignore` reduced the application build context from roughly 68 MB to 726 KB. The stats endpoint now computes total, unique, recent, and timestamp-bound aggregates in one PostgreSQL query, reducing its database round trips from six to three while retaining two independent top-10 breakdown queries.

Redis fault injection confirmed redirect fallback to PostgreSQL. PostgreSQL fault injection confirmed warm-cache redirects remain `302` and cache misses return `503`; this test exposed and led to a fix for transaction-acquisition failures previously returning `500`. The read-only k6 comparison initially measured 4.88 ms warm-cache p95 and 416.99 ms Redis-unavailable fallback p95 after reducing failure-detection time from 2.02 seconds. A forced cache-miss run then measured the PostgreSQL path at 7.91 ms p95. After adding a three-failure, five-second Redis circuit breaker, sustained-outage p95 measured 8.74 ms and recovered-cache p95 measured 6.85 ms, with 0% failures in every run.

`./mvnw -Psecurity dependency-check:check` passes the CVSS 7 gate with no reported vulnerable dependency after overriding Swagger UI to 5.32.14. The supported zero-setup runtime is `docker compose up --build`; direct host `spring-boot:run` intentionally requires separately reachable PostgreSQL and Redis instances.
