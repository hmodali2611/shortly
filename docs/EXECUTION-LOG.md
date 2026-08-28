# Execution Log — AI-Assisted Engineering Record

**Companion to:** `design.md` (what was built and why), `SCENARIOS.md` (three worked walkthroughs)
**This document covers:** how AI was used, what it produced, what was kept, what was rejected, and who signed off.
**Cross-reference convention:** `§n` alone always refers to a section of *this* document. References to another document are always written with its filename — `design.md §9`, `SCENARIOS.md §2.4`.

> **Evidence status.** The implementation, Docker-backed integration, k6 smoke test, and OWASP/NVD dependency gate are complete. Entries marked ⬜ require human sign-off and are intentionally not claimed. Narrative test IDs below describe scenario acceptance criteria; the executable evidence is the test source and the measured results in §6.

---

## 1. Operating principle

The assignment's framing — *AI assists the engineer within tasks; the engineer owns execution and quality* — is implemented here as three hard rules:

1. **AI never sees a task it hasn't been given acceptance criteria for.** A prompt without a testable definition of done produces plausible code, which is the most expensive kind.
2. **No AI output is committed on the strength of looking correct.** Every accepted artifact has a named verification: a test ID from `design.md` §11, a build gate, or a manual review note recorded below.
3. **The engineer writes the design; AI writes against it.** Architecture, data model, failure semantics, and security posture were decided first and are inputs to prompts, not outputs of them. This is why `design.md` exists as a separate document written before the code.

---

## 2. Task definition format

Every unit of work is defined before any prompt is issued, using this structure. This is the discipline the assignment calls "intent, constraints, acceptance criteria, and technical context."

```
TASK    <id> — <one-line outcome>
INTENT  Why this exists. What breaks or is missing without it.
CONTEXT Files, interfaces, and prior decisions the implementation must respect.
        (Design section references, not restated prose.)
CONSTRAINTS
        Non-negotiables: dependency limits, package boundaries, performance,
        security posture, style. Explicitly including what NOT to do.
ACCEPT  Testable statements. Each maps to a test ID or a build gate.
RISK    What goes wrong if this is subtly wrong, and how that would be noticed.
```

**Worked example — the cache-TTL task, the one that mattered most:**

```
TASK    G-07 — Cache-aside resolution for GET /{code}
INTENT  Redirect p95 <50ms at 350 rps peak (NFR-1). Postgres must not be
        on the hot path when warm.
CONTEXT design.md §9. Existing: LinkResolver (Postgres-only, tested by T-1).
        Package boundary: redirect/ must not call management/.
CONSTRAINTS
        - Redis is a cache ONLY. Never on the write path (design.md §6).
        - Redis unavailable MUST NOT fail a redirect (NFR-1).
        - Cache TTL must never outlive the link: min(defaultTtl, remaining).
        - No new dependencies beyond spring-boot-starter-data-redis.
ACCEPT  T-5 (expired link with warm cache still returns 410)
        T-6 (Redis container stopped mid-run, redirects continue)
        T-1 still passes unmodified — the seam did not change.
RISK    A cache that outlives expiry serves revoked links. That is a security
        defect, not a staleness bug, and it is invisible in a green test run
        unless T-5 exists. T-5 is written BEFORE the implementation.
```

The `RISK` line is why T-5 was written first. That ordering is the whole point of the format.

---

## 3. Traceability ledger

**Disposition key:** ✅ accepted as generated · ✏️ accepted after engineer edit · ❌ rejected · 🔁 regenerated after refinement

| # | Artifact | AI role | Disposition | Rationale | Verified by | Sign-off |
|---|---|---|---|---|---|---|
| 1 | `Base62` encode/decode | Full generation from spec | ✏️ | Correct algorithm; used `String` concatenation in a loop. Replaced with `StringBuilder`. Cosmetic at 8 chars, but the hot-path habit matters. | Unit tests, SpotBugs | ⬜ |
| 2 | `ShortCodeGenerator` | Full generation | ❌ → 🔁 | First output used `java.util.Random`. Non-negotiable violation of FR-6 — `Random` is a 48-bit LCG and predictable from prior outputs. Re-prompted with the constraint stated explicitly; second output used `SecureRandom`. **Recorded because this is exactly the failure mode that passes review by looking right.** | T-3/T-15; 9 focused tests | Harika Modali, 2026-08-27 |
| 3 | Flyway migrations | Generation from `design.md` §5 | ✏️ | The initial links DDL used `VARCHAR(16)` for `short_code`, reproducing the design's own conflict with 32-character aliases. It was corrected to 32. A populated V1/V2-to-V3 test now proves link/click preservation while ownership data and its index are intentionally removed. **AI does not review your design; it inherits it.** | Fresh Testcontainers migration; populated upgrade test | Harika Modali, 2026-08-27 |
| 4 | `UrlSafetyValidator` (SSRF) | Generation + adversarial review | ✏️ | Generated allowlist and private-range checks correctly. Missed `169.254.169.254` as a distinct case and did not handle IPv6-mapped IPv4 (`::ffff:10.0.0.1`) bypass. Second pass, prompted adversarially ("how would you bypass this?"), surfaced both. DNS resolution was then made injectable so public, mixed-address, IPv6, unresolved-host, and exact `422` behavior could be tested deterministically. | T-7/T-8; 12-case URL-safety suite | Harika Modali, 2026-08-27 |
| 5 | Test plan T-1…T-25 and implemented test bodies | Generation from test table | ✏️ | Strong at Testcontainers boilerplate. Weak at concurrency: proposed T-2 used two sequential calls, which does not test the race at all. The invalid test was rejected; the concurrent constraint and mutation checks are retained as out-of-scope validation rather than claimed as tests. | Current automated suite; evidence status per ID in `design.md` §11 | ⬜ |
| 6 | `GlobalExceptionHandler` / RFC 7807 | Full generation | ✅ | Mechanical mapping from the `design.md` §7 status table. Low-risk, high-tedium — the ideal case for delegation. | T-10, error-shape assertions | ⬜ |
| 7 | `ClickRecorder` bounded queue | Generation | ❌ | Proposed an unbounded `LinkedBlockingQueue`. Directly contradicts `design.md` §10's "drop under load rather than OOM." Written by hand instead: bounded queue, `offer()` with an internal drop counter. Rejected rather than edited because the fix is the entire design decision. Crash durability, exported metrics, and redirect-latency measurement remain out of scope. | Queue saturation and multi-batch/restore unit tests | Harika Modali, 2026-08-27 |
| 8 | Javadoc + README | Generation from code + design | ✏️ | Accurate but inflated — described the prototype's guarantees in production terms. Trimmed to match `design.md` §1's scope boundary. **AI defaults to flattering the artifact.** | Manual read against `design.md` §1 | ⬜ |
| 9 | Load-test harness (k6) | Generation | ✏️ | Fine harness; initial script measured only cache-warm reads, which would have produced a flattering and meaningless p95. Added cold-start and mixed read/write phases. | T-12, recorded result | ⬜ |
| 10 | Deletion cache eviction | Review + implementation | ✏️ | Review found eviction occurred before transaction commit, allowing a concurrent cache miss to repopulate active data. A new ordering test failed against the old code; eviction now runs after successful commit. | T-10; 8 focused service tests; Docker-backed gate | Harika Modali, 2026-08-27 |
| 11 | Redirect status and failure semantics | Review | ✅ | Active links use `302`; unknown links use `404`; expired/deleted links use `410`; cache failures fall through and unavailable storage maps to `503`. Dedicated HTTP RFC 7807 assertions for `404` and `503` remain planned. | Integration lifecycle; resolver unit tests; fault injection | Harika Modali, 2026-08-27 |
| 12 | Redis cache circuit breaker | Measurement-led implementation | ✏️ | A cache-miss/PostgreSQL-path baseline of 7.91 ms p95 showed that the previous 416.99 ms Redis-outage p95 was dominated by failure detection. Added a three-failure, five-second circuit with one half-open recovery probe; sustained-outage p95 fell to 8.74 ms and recovered-cache p95 measured 6.85 ms, all with 0% failures. | Breaker/cache unit tests; Redis stop/restart k6 fault injection | Harika Modali, 2026-08-27 |
| 13 | `RedirectControllerHotPathTest` (T-11 stalled-sink latency, T-22/NFR-6 log redaction) | Full generation, closing two previously-planned acceptance criteria | ✏️ | The stalled-sink test's first version passed for the wrong reason: it stubbed `JdbcTemplate.batchUpdate` to return a value typed `int[]`, but the real four-argument overload returns `int[][]`. The resulting `ClassCastException` was silently caught by `ClickFlusher`'s own `catch (RuntimeException)` failure path, so the test reported a passing drop count without ever proving the sink was genuinely stalled. Found only by tracing the drop count by hand against expected queue-capacity arithmetic (4 capacity, 50 offers, 46 expected drops — one extra appeared) rather than by any assertion failing in an obviously diagnostic way. Fixed to return `int[0][0]`. **The bug was in test code, not production code, and it is exactly the same failure shape ledger entries 2 and 7 describe in production code: idiomatic, compiling, and wrong about a property no type signature enforces.** The log-redaction test required no correction — it asserts against real Logback output with no mocked return-type surface to get wrong. | Both tests pass under `mvn test`; full-suite run confirmed 41/41 with zero regressions | Harika Modali, 2026-08-27 |
| 14 | `RedisCircuitBreaker` internal simplification | Review-driven cleanup, no behavior change requested | ✏️ | Flagged as over-engineered: state was packed into an `AtomicLong` with sentinel values (`0`=closed, `-1`=half-open, else=open-until-timestamp) plus a separate `AtomicInteger`, driven by `compareAndSet` — lock-free machinery for a control path touched only on Redis cache calls, not a hot loop. Rewritten as an `enum State` with plain fields behind `synchronized` methods; `RedisCircuitBreakerTest`'s exact `clock.millis()` call sequence was traced by hand first to confirm the rewrite could reproduce it call-for-call before touching the file. | `RedisCircuitBreakerTest` unchanged and passing; full non-Docker suite (41 tests, pre-entry-16) green; JaCoCo critical-service ratio held at 95.8%, though the class's own line count grew by 2 (21→23 covered), only caught when the aggregate was re-verified for ledger entry 16 | Harika Modali, 2026-08-27 |
| 15 | `OperationsController` (`/healthz`, `/readyz`) | Reviewed for the same over-engineering pass; **not changed** | — | Also flagged as reinventing Spring Boot Actuator's built-in Kubernetes probes. On inspection this doesn't hold up as a safe fix: Actuator's probe endpoints live at `/actuator/health/liveness` and `/actuator/health/readiness` by default, and getting the required top-level `/healthz`/`/readyz` paths (FR-9, `openapi.yaml`, the Compose healthcheck) needs *some* adapter regardless. The tempting rewrite — swap the current `HealthEndpoint.health().getStatus()` call for Spring's `ApplicationAvailability` bean — would have been a regression, not a simplification: the raw `ReadinessState` on that bean does not automatically reflect other health indicators (PostgreSQL, Redis) going down, only the aggregate `HealthEndpoint` query does, which is what `/readyz` needs to detect a dependency outage. Reverting a working, minimal 30-line adapter for a plausible-looking "use the framework" change would have quietly broken NFR-9's dependency-outage signal. Rejecting a change is also a disposition worth recording. | Traced Spring Boot's default health-group composition against the current implementation; no test change | Harika Modali, 2026-08-27 |
| 16 | `RateLimiterTest` (T-9), `AliasValidatorTest` max-length cases (T-14), `GlobalExceptionHandlerTest` (T-23) | Full generation, closing three previously-planned acceptance criteria | ✅ | Three independent, self-contained unit tests against existing mocking conventions already established in `LinkCacheTest`/`UrlSafetyValidatorTest`, so no new pattern was introduced. T-9 mocks `StringRedisTemplate` to assert `429` over the configured limit and `503` on a Redis failure — NFR-2's rate limiter had zero automated coverage before this. T-14 asserts a 32-character alias passes and a 33-character one is rejected, directly guarding the boundary ledger entry 3's `VARCHAR(16)`-vs-32 migration bug lived in; this closes the validator half only — no automated test proves the `links.short_code` column itself accepts 32 characters. T-23 asserts the RFC 7807 envelope (`status`, `title`, `detail`, `type`, `instance`) for a representative `404` and `503` directly against `GlobalExceptionHandler`, rather than through a full HTTP round trip. | All three pass under `mvn test`; full Docker-backed `mvn verify` confirmed 48/48 with zero regressions; JaCoCo critical-service aggregate re-verified at 95.8% (160/167) | Harika Modali, 2026-08-27 |
| 17 | Authentication: shared API key + hashed owner reference | Full generation, then rejected after review | ❌ | An earlier iteration implemented `ApiKeyAuthenticator` (constant-time comparison against a configured list of static keys) and `OwnerKeyHasher`, storing the hash in an `owner_key_id` column so a caller who presented the matching key could be treated as a link's "owner." Rejected on review: a single shared secret cannot express *per-caller* ownership — every holder of the one key is indistinguishable from every other — and using it from the dashboard would mean embedding it in a page every visitor loads, which defeats the purpose of having it at all. Removed entirely rather than patched, because the fix is a different authentication model, not a bug in this one. `V3__remove_link_ownership.sql` drops the column and its index; the populated V1/V2-to-V3 migration test (ledger entry 3) proves link and click data survive the removal. The scope decision made instead: full anonymous access for this prototype (`RISKS-AND-VALIDATION.md`), with the production-correct design — per-user bearer token issued after real authentication, hashed or referenced (never the raw token) and enforced as ownership on every metadata/analytics/delete call — documented but intentionally not built (`ARCHITECTURE.md`; `design.md` §10, §14 item 7). | Migration test (ledger entry 3); manual review of the removed code against `design.md`'s stated authentication requirements | Harika Modali, 2026-08-27 |
| 18 | `GlobalExceptionHandler` catch-all + `LinkService` database-outage mapping | Review-driven fix, from an independent architect-lead review of the finished submission | ✏️ | The reviewer verified nine specific implementation claims against source rather than the docs, and found two that didn't hold: `GlobalExceptionHandler` had no fallback for any exception outside `ApiException`/`MethodArgumentNotValidException`, and `LinkService.metadata()`/`delete()` (unlike `LinkResolver` on the redirect path) didn't catch `DataAccessException`/`CannotCreateTransactionException` at all. Together this meant a Postgres outage during a metadata or delete call bypassed the RFC 7807 envelope entirely and fell through to Spring Boot's default error body — contradicting both NFR-7 and design.md §9's own failure table, which already claimed `503` for that path without the code actually producing it. Fixed by adding a `{DataAccessException, CannotCreateTransactionException} → 503` handler and a catch-all `Exception → 500` handler (both logged server-side via SLF4J, neither echoing exception detail to the client), and by wrapping `LinkService.requireLink()` in the same pattern `LinkResolver` already used. **The same failure shape as ledger entries 2, 7, and 13: a claim that read as true because nothing exercised the path that would have proven it false.** | `GlobalExceptionHandlerTest` (3 new cases), `LinkServiceTest` (2 new cases); full Docker-backed `mvn verify` gate: 58/58 tests, Spotless clean, 0 SpotBugs findings, JaCoCo floor met | Harika Modali, 2026-08-28 |
| 19 | `UrlSafetyValidator` IPv6 Unique Local Address check | Review-driven fix, from the same independent review | ✏️ | The same review found that `isSiteLocalAddress()` only recognizes the deprecated IPv6 site-local range (`fec0::/10`), not the modern Unique Local Address range (`fc00::/7`, RFC 4193) that replaced it — so a target hostname resolving to an `fd00::/8` address passed SSRF validation undetected. This wasn't caught by the original adversarial-review prompt (ledger #4, §4.1), which surfaced IPv4-mapped bypass and the cloud metadata endpoint but not this range. Fixed by adding an explicit `fc00::/7` check alongside the existing five `InetAddress` predicates. | `UrlSafetyValidatorTest`: boundary cases at `fc00::1` and `fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff` (rejected), `fe00::1` (accepted, confirms the check doesn't overreach); full Docker-backed gate (58/58) | Harika Modali, 2026-08-28 |

**Pattern worth naming.** The rejections cluster in one place: entries 2, 7, and the concurrency half of 5 are all cases where the AI produced *idiomatic, compiling, plausible* code that violated a stated non-functional constraint. It was reliable on structure and syntax, and unreliable precisely where the design's reasoning lived. That is the boundary this project treats as the human's responsibility.

---

## 4. Representative prompt evidence

The original build's vendor chat transcript is not a submission artifact. The SSRF sequence below was retained as a sanitized prompt iteration. The brownfield, ambiguous, and task-level examples are explicitly labelled reconstructions from contemporaneous tasks, design decisions, dispositions, code, and validation records; they preserve intent and constraints but are not presented as verbatim chat history. §4.5 is different: it follows a later AI-pairing session prompt by prompt, with shorthand polished for readability.

### 4.1 Greenfield security — retained prompt iteration

The SSRF validator (ledger #4), showing why the first prompt was insufficient rather than just showing the final one.

**Attempt 1** — *"Write a Java validator that blocks SSRF in user-supplied URLs."*
Output: scheme check plus a hardcoded list of private CIDR strings compared against the *hostname text*. Blocks `http://10.0.0.1`, fails on `http://internal.example.com` resolving to `10.0.0.1`. Textual matching where resolution was required.

**Attempt 2** — added: *"Resolve the hostname to IP addresses first. Block loopback, private, link-local, and unqualified hosts. Reject if ANY resolved address is blocked."*
Output: structurally right, used `InetAddress.getByName()` — returns only the first address, so a multi-A-record host with one public and one private address passes.

**Attempt 3** — added: *"Use getAllByName and reject if any address fails. List every bypass you can think of against this code."*
Output: correct implementation, plus it surfaced IPv6-mapped IPv4 and the cloud metadata endpoint unprompted.

**Engineer addition, not AI-surfaced:** the DNS-rebinding gap in `design.md` §10 — validation and redirection are different moments and no amount of validation-time checking closes it. The AI hardened what it was pointed at; recognising that the *entire approach* has a time-of-check/time-of-use hole was human work, and it is documented as a known limitation rather than papered over.

**Generalisation applied to later prompts:** state the threat model, not the feature. "Block SSRF" is a label; "reject if any resolved address is in these ranges, and here is the attacker's capability" is a specification.

### 4.2 Brownfield caching — sanitized reconstruction

**Provenance:** reconstructed from task G-07 in §2, ledger entries 10–12, `SCENARIOS.md` §2, and the associated tests and fault-injection evidence. It is not a verbatim transcript.

**Initial ask** — *"Redirects are too slow under load. Add Redis caching."*

This was treated as a proposed solution, not an implementable requirement. It omitted the latency target, failure behavior, expiry semantics, invalidation, and the existing rate limiter's Redis dependency.

**Refined task prompt:**

```
Analyze the existing redirect path before changing code. Identify every impacted
module, API, data flow, and regression contract. Then implement cache-aside
resolution for GET /{code} with these constraints:

- warm redirect p95 target: below 50 ms at the modelled load;
- PostgreSQL remains authoritative and Redis stays off the write path;
- Redis failure must fall through to PostgreSQL;
- cache TTL must be min(default TTL, remaining link lifetime);
- deletion must not leave a redirectable cached value;
- do not change analytics behavior or the public redirect contract.

Acceptance: T-1 remains green; T-5 proves a warm cache cannot outlive expiry;
T-6 proves Redis-down fallback; deletion ordering is covered by a regression test.
```

**Measurement-led follow-up prompt (reconstructed):**

```
Compare Redis-unavailable redirect latency with a cache-miss PostgreSQL baseline.
If Redis failure detection dominates, add a small cache-only circuit breaker:
open after three consecutive failures, bypass Redis for five seconds, and allow
exactly one half-open recovery probe. Preserve PostgreSQL fallback and every
existing redirect, expiry, deletion, and analytics contract.
```

**Output and disposition:** the impact map was accepted after engineer review. A generated fixed 24-hour TTL was rejected because it could serve expired links. Review later found pre-commit deletion eviction could race with cache repopulation; eviction was moved to after successful commit. The latency comparison justified the circuit breaker: sustained Redis-outage p95 fell from the previous 416.99 ms fallback measurement to 8.74 ms after the circuit opened, then measured 6.85 ms after recovery.

**Validation:** focused resolver/service/breaker tests, the 39-test Docker-backed gate, Redis/PostgreSQL fault injection, and measured warm, degraded, and recovered latency. **Sign-off:** Harika Modali, 2026-08-27.

### 4.3 Ambiguous analytics — sanitized reconstruction

**Provenance:** reconstructed from ledger entry 7, `SCENARIOS.md` §3, `design.md` §§3 and 10, and analytics tests. It is not a verbatim transcript.

**Initial ask** — *"Add analytics so users can see how their links perform."*

**Clarification prompt:**

```
Do not implement yet. Identify the decisions hidden in this requirement:
what counts as a click, required freshness, counters versus events, how unique
visitors are represented without storing PII, and the durability guarantee.
For each ambiguity, give options, trade-offs, and the question a product owner
must answer.
```

The resulting options were normalized by the engineer: count successful `302` responses, expose eventual consistency through `asOf`, retain event rows, use a configured HMAC secret with the day included in the message, and prefer bounded best-effort delivery over redirect failure.

**Implementation prompt:**

```
Implement the normalized analytics requirement without blocking redirects.
Use a bounded in-memory queue and nonblocking offer; drop and count excess
events rather than allowing unbounded growth. Persist click events in scheduled
batches, restore a failed batch when capacity permits, and derive stats from
events. Do not store raw IP addresses.

Acceptance: queue saturation is bounded and observable through the internal
drop counter; successful redirects enqueue events; failed writes restore events;
stats include asOf; all existing redirect tests remain green.
```

**Output and disposition:** the generated unbounded `LinkedBlockingQueue` was rejected because a stalled sink could exhaust the JVM and take down redirects. The bounded queue implementation was written under engineer control.

**Validation:** queue-saturation, multi-batch, failed-write restoration, stats aggregation, and Docker-backed lifecycle tests. **Sign-off:** Harika Modali, 2026-08-27.

### 4.4 Additional task prompts — polished reconstructions

**Provenance:** these are representative reconstructions, not quotations. Each is grounded in the named ledger entry, resulting implementation, and validation evidence. They show the working prompt shape without claiming unavailable chat text as historical fact.

#### Secure short-code refinement

Grounded in ledger #2 and T-3/T-15.

```
Replace the generated short-code implementation after security review. Generate
exactly eight characters from the Base62 alphabet with SecureRandom; do not use
Random, sequential IDs, timestamps, or a Redis counter. Keep PostgreSQL's unique
constraint authoritative and retry at most five generated-code collisions.

Acceptance: alphabet and length tests pass; forced collisions retry; five
collisions return 503; Redis remains outside the creation write path.
```

**Disposition:** accepted after the original `Random`-based output was rejected. The refinement preserved non-enumerability and bounded failure behavior.

#### Schema-to-contract consistency review

Grounded in ledger #3, `AliasValidator.MAX_LENGTH`, and `FlywayMigrationTest`.

```
Review the Flyway link schema against the API's custom-alias contract. Check
column widths, nullability, indexes, and upgrade safety instead of reviewing SQL
in isolation. A 32-character valid alias must fit without truncation. Preserve
existing links and click events while removing obsolete ownership data.

Acceptance: fresh migration succeeds; populated V1/V2 data survives V3; the
ownership column and index are removed deliberately, not by destructive reset.
```

**Disposition:** edited. The review exposed the inherited `VARCHAR(16)` mismatch, corrected it to 32, and added a populated upgrade test.

#### Transaction-safe cache invalidation

Grounded in ledger #10 and `LinkServiceTest`.

```
Review link deletion for cache/database ordering races. PostgreSQL is
authoritative, deletion is transactional, and a failed transaction must not
evict a still-active mapping. Avoid a design where a concurrent cache miss can
repopulate active data between eviction and commit.

Acceptance: cache eviction occurs only after commit; rollback performs no
eviction; deleted links continue to return 410.
```

**Disposition:** edited after review. Pre-commit eviction was replaced with transaction synchronization that evicts only after a successful commit.

#### Degraded-mode validation

Grounded in ledger #11–12, T-6/T-13, and the recorded fault-injection runs.

```
Design a failure test matrix for Redis and PostgreSQL independently. Do not
treat all dependency failures alike: cache reads fail open to PostgreSQL, while
the creation rate limiter fails closed. Preserve cached redirects when the
database is unavailable and map uncached storage failures to 503.

Measure warm-cache, cache-miss, sustained Redis-outage, and recovered latency.
Recommend resilience code only when the measurements identify the bottleneck.
```

**Disposition:** accepted after measurement. The matrix exposed Redis failure detection as the degraded-path cost and justified the small cache-only circuit breaker.

#### Authentication-model challenge

Grounded in ledger #17 and migration V3.

```
Evaluate whether a shared static API key plus a hashed owner reference provides
real per-caller authorization for metadata, analytics, and deletion. Include how
the browser dashboard would obtain the key and whether different users can be
distinguished. Prefer removing misleading security over retaining a control
that cannot enforce the stated ownership boundary.

Return a keep, redesign, or remove decision with migration impact.
```

**Disposition:** rejected and removed. A shared key could not represent ownership and could not safely be embedded in the dashboard; the production path now requires authenticated identities and revocable per-user tokens.

#### Evidence reconciliation

Grounded in ledger #5, #13, #16, the 49-test gate, and the final documentation review.

```
Audit every claimed acceptance criterion against executable tests or recorded
manual evidence. Do not infer execution from a T-ID, test name, coverage number,
or stale Surefire report. Mark each item automated, manual, partial, or out of
scope; preserve historical counts at the time they were recorded.

Acceptance: the latest Maven summary, Java-file count, JaCoCo numerator and
denominator, and documentation totals agree; unsupported claims are removed.
```

**Disposition:** accepted with corrections. It separated executable evidence from planned criteria, preserved the historical 39- and 41-test milestones, and recorded the current 49-test gate without rewriting earlier evidence.

### 4.5 Post-build review session — prompts polished for readability

**Provenance:** §4.1–4.4 contain retained or reconstructed original-build evidence. This section is different in kind: it follows actual prompt sequences from live AI-pairing sessions conducted after the initial build. Rows (a)–(b) are a code-quality review pass that closed several of the "planned, not executed" gaps this document had been carrying. Rows (c)–(d) are a separate, later session: an independent architect-lead review against the assignment brief that verified implementation claims against source rather than re-reading the docs, and the two fixes it produced. The prompts below are polished for readability (live chat shorthand — "do it," "leave it" — does not read well as a submission artifact) but preserve what was asked and in what order.

| # | Prompt (polished) | What it produced | Disposition |
|---|---|---|---|
| a | "What are the highest-value fixes to close before this goes to review, given the remaining time budget?" | Reviewed the test table in `design.md` §11 and recommended closing T-11 (stalled-sink redirect latency) and T-22/NFR-6 (log redaction) as the two highest-value gaps, and explicitly recommended **against** building the `click_daily_agg` rollup or a mutation-testing framework given the remaining time budget. | Accepted — see ledger entry 13. |
| b | "Implement those two fixes and update the affected documentation to match." | Generated `RedirectControllerHotPathTest`. The stalled-sink test's first version passed for the wrong reason: it stubbed `JdbcTemplate.batchUpdate` to return `int[]`, but the real overload returns `int[][]`, so a `ClassCastException` was silently caught by `ClickFlusher`'s own failure-handling path. | ❌ → ✏️ — found by tracing the drop count by hand against expected queue-capacity arithmetic, not by a test failing loudly. Corrected mock return type. Documented in entry 13. |
| c | "Review this project against the assignment brief, as a reviewer and architect lead — verify doc claims against the actual source, not just the docs." | Read every design/scenario/execution doc, then independently verified nine specific implementation claims (cache TTL rule, circuit breaker state machine, fail-open/fail-closed asymmetry, delete-after-commit eviction, `SecureRandom` retry bound, SSRF multi-address resolution, RFC 7807 handler, `IpHasher` construction, bounded non-blocking queue) directly against source, file:line. Surfaced two real gaps: `GlobalExceptionHandler` had no catch-all, and `LinkService.metadata()`/`delete()` didn't map database failures to `503` the way `LinkResolver` does; separately, `UrlSafetyValidator` didn't reject IPv6 Unique Local Addresses. | Accepted — see ledger entries 18–19. |
| d | "Fix both. Keep the change scoped to exactly what the review found — don't refactor around it." | Added `{DataAccessException, CannotCreateTransactionException} → 503` and catch-all `Exception → 500` handlers to `GlobalExceptionHandler`; wrapped `LinkService.requireLink()` in `LinkResolver`'s existing try/catch pattern; added an `fc00::/7` check to `UrlSafetyValidator`. Added 9 tests: `LinkServiceTest` (2 — database-outage mapping for `metadata()`/`delete()`), `GlobalExceptionHandlerTest` (3 — both new handlers, asserting no exception detail leaks into the response), `UrlSafetyValidatorTest` (4 — two new `@ValueSource` cases plus two dedicated boundary tests at `fc00::1`/`fdff:...` and `fe00::1`). | ✅ — full Docker-backed `mvn verify`: 58/58 tests, Spotless clean, 0 SpotBugs findings, JaCoCo floor met. Then the two fixes were reflected back into `design.md` §9/§10 and `RISKS-AND-VALIDATION.md` rather than left to drift from the code — the same discipline entry (a)/(b) and the "evidence reconciliation" prompt (§4.4) already name for this document. |

**Generalization.** The mock-typing bug in (b) was caught by re-deriving the expected queue count by hand and comparing it against the actual output, not by a red test or an obvious error message. That is the same discipline §1's operating principle names for the original build ("no AI output is committed on the strength of looking correct"), applied here to reviewing AI-produced *review* work rather than first-pass generation. It is also the pattern §3's "Pattern worth naming" note describes: plausible, compiling, and wrong about a property nothing in the type system or a passing test enforces.

---

## 5. Secure AI usage

| Rule | Practice |
|---|---|
| No secrets in prompts | No credentials, connection strings, or `.env` contents. Config discussed by shape (`spring.datasource.url`), never by value. |
| No proprietary data | Prototype only; no employer code, internal identifiers, or customer data. |
| Dependencies verified, not trusted | Every AI-suggested dependency checked to exist at the named version and to be current. AI hallucinates plausible artifact coordinates, and a typosquatted coordinate is a supply-chain compromise. The `security` Maven profile runs OWASP Dependency-Check. |
| Generated code is untrusted input | No AI output executed before reading. No copy-paste of shell commands without inspection. |
| Licence hygiene | Generated code reviewed for verbatim recognisable third-party sources. |

---

## 6. Quality gates and sign-off

Evidence from the latest local `./mvnw verify` run:

| Gate | Result | Status |
|---|---|---|
| Build/package | Spring Boot JAR built successfully | ✅ |
| Tests | 58 run, 0 failed, 0 errors, 0 skipped in Docker-backed verification | ✅ |
| Format | Spotless: 46 Java files clean | ✅ |
| Static analysis | SpotBugs: 0 warnings/errors | ✅ |
| Coverage | JaCoCo critical-service aggregate: 96.0% (167/174); enforced floor 80% | ✅ |
| Dependencies | OWASP/NVD CVSS 7 gate passed with no reported vulnerable dependency after the Swagger UI 5.32.14 override | ✅ |
| Load and analytics | 100 links created, 10,000 redirects completed with 0% failures and 32.11 ms p95; all 10,000 click events persisted | ✅ |
| Fault tolerance | Redis-down redirect `302`; PostgreSQL-down warm redirect `302`; PostgreSQL-down cache miss `503` | ✅ |
| Cache comparison | Warm-cache p95 4.88 ms; Redis-unavailable fallback p95 reduced from 2.02 s to 416.99 ms | ✅ |
| Clean-source startup | Artifact-free 775 KB build context; Compose healthy; readiness `UP`; create `201`; redirect `302` | ✅ |
| Build context | `.dockerignore` reduced normal workspace context from about 68 MB to 726 KB; image build passed | ✅ |
| Stats query count | Summary aggregates consolidated into one query; endpoint reduced from six to three database round trips | ✅ |

GitHub Actions runs `./mvnw --batch-mode verify` on Java 25. The hosted build passed after the prompt-evidence commit was pushed, and a separate fresh clone reproduced the full 49-test gate locally. The post-build-review fixes (ledger #18–19) were pushed as `6245ad8`; a fresh clone of that commit reproduced the current 58-test gate the same way — zero failures or skips, Spotless clean, no SpotBugs findings, coverage floor met.

**High-impact changes requiring explicit engineer sign-off before merge**, regardless of gate status: anything in `security/`, any Flyway migration, any change to the code generator, any change to redirect-path failure handling, and any change that alters a documented status code. These are the areas where a passing test suite is weakest evidence — a green build proves the code does what the tests say, and in exactly these areas the risk is that the *tests* encode the wrong belief.

**Standing limitation of the gates.** Coverage measures lines executed, not properties proven. The out-of-scope T-2 alias-race mutation check could fail if database uniqueness were removed while line coverage stayed identical. The gates catch regression and hygiene; they do not catch a design that is confidently wrong.
