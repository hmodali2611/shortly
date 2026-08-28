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
| 5 | Test plan T-1…T-25 and implemented test bodies | Generation from test table | ✏️ | Strong at Testcontainers boilerplate. Weak at concurrency: proposed T-2 used two sequential calls, which does not test the race at all. The invalid test was rejected; the concurrent constraint test and mutation check remain planned rather than claimed. | Current automated suite; evidence status per ID in `design.md` §11 | ⬜ |
| 6 | `GlobalExceptionHandler` / RFC 7807 | Full generation | ✅ | Mechanical mapping from the `design.md` §7 status table. Low-risk, high-tedium — the ideal case for delegation. | T-10, error-shape assertions | ⬜ |
| 7 | `ClickRecorder` bounded queue | Generation | ❌ | Proposed an unbounded `LinkedBlockingQueue`. Directly contradicts `design.md` §10's "drop under load rather than OOM." Written by hand instead: bounded queue, `offer()` with an internal drop counter. Rejected rather than edited because the fix is the entire design decision. Crash durability, exported metrics, and redirect-latency measurement remain out of scope. | Queue saturation and multi-batch/restore unit tests | Harika Modali, 2026-08-27 |
| 8 | Javadoc + README | Generation from code + design | ✏️ | Accurate but inflated — described the prototype's guarantees in production terms. Trimmed to match `design.md` §1's scope boundary. **AI defaults to flattering the artifact.** | Manual read against `design.md` §1 | ⬜ |
| 9 | Load-test harness (k6) | Generation | ✏️ | Fine harness; initial script measured only cache-warm reads, which would have produced a flattering and meaningless p95. Added cold-start and mixed read/write phases. | T-12, recorded result | ⬜ |
| 10 | Deletion cache eviction | Review + implementation | ✏️ | Review found eviction occurred before transaction commit, allowing a concurrent cache miss to repopulate active data. A new ordering test failed against the old code; eviction now runs after successful commit. | T-10; 8 focused service tests; Docker-backed gate | Harika Modali, 2026-08-27 |
| 11 | Redirect status and failure semantics | Review | ✅ | Active links use `302`; unknown links use `404`; expired/deleted links use `410`; cache failures fall through and unavailable storage maps to `503`. Dedicated HTTP RFC 7807 assertions for `404` and `503` remain planned. | Integration lifecycle; resolver unit tests; fault injection | Harika Modali, 2026-08-27 |
| 12 | Redis cache circuit breaker | Measurement-led implementation | ✏️ | A cache-miss/PostgreSQL-path baseline of 7.91 ms p95 showed that the previous 416.99 ms Redis-outage p95 was dominated by failure detection. Added a three-failure, five-second circuit with one half-open recovery probe; sustained-outage p95 fell to 8.74 ms and recovered-cache p95 measured 6.85 ms, all with 0% failures. | Breaker/cache unit tests; Redis stop/restart k6 fault injection | Harika Modali, 2026-08-27 |
| 13 | `RedirectControllerHotPathTest` (T-11 stalled-sink latency, T-22/NFR-6 log redaction) | Full generation, closing two previously-planned acceptance criteria | ✏️ | The stalled-sink test's first version passed for the wrong reason: it stubbed `JdbcTemplate.batchUpdate` to return a value typed `int[]`, but the real four-argument overload returns `int[][]`. The resulting `ClassCastException` was silently caught by `ClickFlusher`'s own `catch (RuntimeException)` failure path, so the test reported a passing drop count without ever proving the sink was genuinely stalled. Found only by tracing the drop count by hand against expected queue-capacity arithmetic (4 capacity, 50 offers, 46 expected drops — one extra appeared) rather than by any assertion failing in an obviously diagnostic way. Fixed to return `int[0][0]`. **The bug was in test code, not production code, and it is exactly the same failure shape ledger entries 2 and 7 describe in production code: idiomatic, compiling, and wrong about a property no type signature enforces.** The log-redaction test required no correction — it asserts against real Logback output with no mocked return-type surface to get wrong. | Both tests pass under `mvn test`; full-suite run confirmed 41/41 with zero regressions | Harika Modali, 2026-08-27 |

**Pattern worth naming.** The rejections cluster in one place: entries 2, 7, and the concurrency half of 5 are all cases where the AI produced *idiomatic, compiling, plausible* code that violated a stated non-functional constraint. It was reliable on structure and syntax, and unreliable precisely where the design's reasoning lived. That is the boundary this project treats as the human's responsibility.

---

## 4. Representative prompt evidence

The exact vendor chat transcript is not a submission artifact. The SSRF sequence below was retained as a sanitized prompt iteration. The brownfield and ambiguous examples are explicitly labelled reconstructions from the contemporaneous task, design, disposition, and validation records; they preserve the constraints and decisions but are not presented as verbatim chat history.

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

The resulting options were normalized by the engineer: count successful `302` responses, expose eventual consistency through `asOf`, retain event rows, use a rotating HMAC-derived IP hash, and prefer bounded best-effort delivery over redirect failure.

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
| Tests | 41 run, 0 failed, 0 errors, 0 skipped in Docker-backed verification | ✅ |
| Format | Spotless: 44 Java files clean | ✅ |
| Static analysis | SpotBugs: 0 warnings/errors | ✅ |
| Coverage | JaCoCo critical-service aggregate: 95.8% (158/165); enforced floor 80% | ✅ |
| Dependencies | OWASP/NVD CVSS 7 gate passed with no reported vulnerable dependency after the Swagger UI 5.32.14 override | ✅ |
| Load and analytics | 100 links created, 10,000 redirects completed with 0% failures and 32.11 ms p95; all 10,000 click events persisted | ✅ |
| Fault tolerance | Redis-down redirect `302`; PostgreSQL-down warm redirect `302`; PostgreSQL-down cache miss `503` | ✅ |
| Cache comparison | Warm-cache p95 4.88 ms; Redis-unavailable fallback p95 reduced from 2.02 s to 416.99 ms | ✅ |
| Clean-source startup | Artifact-free 775 KB build context; Compose healthy; readiness `UP`; create `201`; redirect `302` | ✅ |
| Build context | `.dockerignore` reduced normal workspace context from about 68 MB to 726 KB; image build passed | ✅ |
| Stats query count | Summary aggregates consolidated into one query; endpoint reduced from six to three database round trips | ✅ |

GitHub Actions runs `./mvnw --batch-mode verify` on Java 25. A passing hosted workflow still needs to be captured after the repository is pushed.

**High-impact changes requiring explicit engineer sign-off before merge**, regardless of gate status: anything in `security/`, any Flyway migration, any change to the code generator, any change to redirect-path failure handling, and any change that alters a documented status code. These are the areas where a passing test suite is weakest evidence — a green build proves the code does what the tests say, and in exactly these areas the risk is that the *tests* encode the wrong belief.

**Standing limitation of the gates.** Coverage measures lines executed, not properties proven. T-2 (the alias race) is the only test in the suite that would fail if its underlying mechanism were removed while coverage stayed identical. The gates catch regression and hygiene; they do not catch a design that is confidently wrong.
