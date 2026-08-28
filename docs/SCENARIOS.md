# Three Scenarios

**Companion to:** `design.md` (the design), `EXECUTION-LOG.md` (the AI-usage record)
**Cross-reference convention:** `§n.n` alone always refers to a section of *this* document (e.g. §2.4). References to another document are always written with its filename — `design.md §9`, `EXECUTION-LOG.md §3`.

Each scenario shows the same five-stage loop applied to a different class of problem: **understand → decompose → execute with AI → validate → sign off.** They are ordered as they occurred in the build (`design.md` §12), because the brownfield scenario only exists as a brownfield scenario if the greenfield one came first.

The three are deliberately different in character:

| | Greenfield | Brownfield | Ambiguous |
|---|---|---|---|
| Hard part | Getting the design right up front | Not breaking what works | Deciding what the requirement means |
| AI is strongest at | Generating from a clear spec | Impact analysis across files | Enumerating options |
| AI is weakest at | Choosing between valid designs | Knowing what it must not touch | Recognising the question is underspecified |
| Human owns | The decision | The blast radius | The interpretation |

---

# Scenario 1 — Greenfield: create and redirect

*Build steps 1–3. From empty repository to a working end-to-end redirect.*

## 1.1 Requirement understanding

Stated requirement: *"Create a short link from a long URL and redirect on lookup."*

Two words carry hidden decisions.

**"Short link"** — how short, and generated how? This determines key-space sizing, collision handling, enumerability, and whether Redis is on the write path. It is the highest-leverage decision in the system and it is invisible in the requirement text. Resolved in `design.md` §6.

**"Redirect"** — with which status code? `301` and `302` are both correct English and produce materially different products: `301` is browser-cached, so repeat clicks never reach the service, click counts silently undercount, and the link becomes un-revocable in practice. Choosing `302` is choosing analytics and revocability over infrastructure cost. Resolved in `design.md` §7.

**Normalized problem statement:** *A write path that maps a validated URL to an unguessable 8-character code with exactly-one-target guarantees under concurrency, and a read path that resolves that code to a `302` in under 50 ms server-side.*

## 1.2 Decomposition

```
G-01 Flyway migration: links table
  └─ G-02 Base62 encode/decode                    (unit-testable in isolation)
       └─ G-03 ShortCodeGenerator (SecureRandom + bounded retry)
            ├─ G-04 POST /api/v1/links
            └─ G-05 GET /{code}
                 └─ G-06 RFC 7807 error handling  (spans both)
```

**Sequencing rationale.** G-02 before G-03 because encoding is pure and testable without a database; G-03 before G-04 because collision handling is the risky part and should not be debugged through an HTTP layer; G-04 before G-05 because you cannot test a redirect without something to redirect to. G-06 last, once the failure modes are known rather than guessed.

**Acceptance criteria per task** were written before implementation — the format is in `EXECUTION-LOG.md` §2.

## 1.3 AI-assisted execution

| Task | What AI did | Disposition |
|---|---|---|
| G-01 | Generated DDL from `design.md` §5 | ✏️ — reproduced the design's own `VARCHAR(16)` / 32-char-alias contradiction verbatim |
| G-02 | Generated encode/decode | ✏️ — correct; `String` concatenation replaced with `StringBuilder` |
| G-03 | Generated generator | ❌ → 🔁 — used `java.util.Random`; see below |
| G-04/05 | Generated controllers and DTOs | ✅ — mechanical against a fixed contract |
| G-06 | Generated `@RestControllerAdvice` | ✅ — direct mapping from the `design.md` §7 status table |

**The G-03 rejection is the one worth reading.** The first output was clean, idiomatic, well-named, and used `java.util.Random` — a 48-bit linear congruential generator whose entire future output is derivable from two observed values. Against FR-6 (generated codes must be collision-resistant and non-enumerable) this is a total failure, and it is invisible to compilation, to linting, and to any test that only asserts the code is 8 characters of base62. Re-prompted with the constraint stated explicitly, the second output used `SecureRandom` and was accepted.

The generalisable lesson, applied to every later prompt: **AI is reliable on structure and unreliable on the properties the design exists to guarantee.** Test the property, not the shape.

**Design decision AI did not make.** Prompted for options, it offered a Redis `INCR` counter as the "standard" approach — and it is standard. It is also wrong here, because it places Redis on the write path, meaning Redis down would mean no links can be created at all. Random generation keeps Redis purely a cache, which makes NFR-1's Redis degradation behavior testable rather than merely claimed. AI enumerated the options competently; choosing between them on a reliability argument was human work.

## 1.4 Validation

- Unit tests verify forced collision retry and retry exhaustion behavior.
- The Testcontainers integration test covers create, redirect, metadata, stats, delete, and health when Docker is available.
- Latest local result: all 49 tests pass with zero skips in Docker-backed verification.
- A unique-constraint mutation check remains a proposed strengthening exercise; it is not claimed as executed.

## 1.5 Risks and sign-off

| Risk | Control |
|---|---|
| Weak randomness → enumerable codes | `SecureRandom` asserted; `security/` changes require sign-off |
| Retry exhaustion undefined | `503` after exactly five attempts, verified by T-15 |
| `301` chosen by habit | Decision documented with its cost in `design.md` §7 |

**Signed off:** Harika Modali, 2026-08-27 — schema migration and secure code generation reviewed against focused tests and the 34-test Docker-backed gate.

---

# Scenario 2 — Brownfield: introducing the Redis cache

*Build step 7. Deliberately sequenced late so the cache lands on an existing, tested, working system — which is what makes it a real brownfield change rather than a greenfield one wearing a costume.*

## 2.1 Requirement understanding

Stated requirement: *"Redirects are too slow under load. Add caching."*

Reframed before touching code: **the requirement is a latency target, not a cache.** "Add caching" is a proposed solution presented as a requirement. Accepting it verbatim skips the question of whether the database is actually the bottleneck.

The read-only k6 harness measured 4.88 ms warm-cache p95 with 0% failures. A Redis-unavailable run initially measured 2.02 seconds p95 because cache failure detection dominated the database fallback; reducing configurable Redis command/connect timeouts to 200 ms lowered fallback p95 to 416.99 ms. A later cache-miss benchmark measured the PostgreSQL path at 7.91 ms p95, confirming that failure detection rather than the database dominated the degraded result. A three-failure, five-second Redis circuit breaker then reduced sustained Redis-outage p95 to 8.74 ms; after Redis recovery, a half-open probe restored caching and the same workload measured 6.85 ms p95.

**Normalized:** *Reduce redirect p95 below 50 ms server-side at 350 rps, without introducing a dependency whose failure can fail a redirect, and without altering any currently-passing behavior.*

## 2.2 Codebase reasoning — impact analysis

This is the stage the assignment names explicitly, and the stage AI genuinely accelerates: given the tree and the design doc, it produced a first-pass impact map in one prompt that would have taken an hour by hand. It was then checked by hand, and the check found something. The sanitized reconstructed task prompt and its provenance are recorded in `EXECUTION-LOG.md` §4.2.

| Component | Impact | Note |
|---|---|---|
| `redirect/LinkResolver` | **Modified** | Cache-aside logic inserted. The only behavioral change. |
| `redirect/LinkCache` | **New** | New abstraction; sole Redis touch point. |
| `management/LinkService` | **Modified** | Must evict on delete, or a deleted link keeps redirecting from cache. |
| `config/` | **Modified** | Redis connection, serializer, timeouts. |
| `analytics/*` | **Untouched** | Off the response path already. |
| `security/RateLimiter` | **Not modified, but coupled** | Already uses Redis. A shared connection-pool exhaustion now affects both. |
| T-1, T-4, T-10 | **Must pass unmodified** | Regression contract. |

**Two findings the impact map surfaced that "add caching" did not:**

**(a) Delete must evict.** Nothing in the requirement mentions delete. Without eviction, `DELETE` returns `204`, the caller believes the link is revoked, and it keeps redirecting until TTL expiry. A silent, security-relevant correctness bug in a module the requirement never mentioned.

**(b) The shared-Redis coupling.** Rate limiting already depended on Redis. Adding the cache means one dependency now serves a security control and a performance optimisation, which need *opposite* failure behavior: the limiter must fail **closed** (`503` on create) and the cache must fail **open** (fall through to Postgres). Discovered by reading the existing code, not from the requirement. Written into `design.md` §9.

## 2.3 Decomposition

```
B-01 Redis via Compose + config, no code path        (infrastructure only)
  └─ B-02 LinkCache abstraction + serialization      (no wiring yet)
       └─ B-03 T-5 written and RED — cache must not outlive expiry
            └─ B-04 Cache-aside in LinkResolver      (T-5 goes green)
                 ├─ B-05 Eviction on delete          (finding (a))
                 ├─ B-06 Degradation path + T-6      (finding (b), fail-open)
                 ├─ B-07 Negative caching, 30s
                 └─ B-08 Circuit breaker              (3 failures, 5s, one probe)
```

**B-03 before B-04 is the deliberate ordering.** The failing test is written before the implementation that would satisfy it, because the TTL rule is the one thing a working cache can silently get wrong.

## 2.4 The defect this scenario exists to demonstrate

The generated cache-aside implementation was correct, conventional, and would pass code review:

```java
redis.opsForValue().set(key, link, Duration.ofHours(24));   // fixed TTL
```

A link expiring in 5 minutes, cached for 24 hours, **keeps redirecting for 23 hours and 55 minutes past its expiry.** Expiry is a revocation mechanism, so this is a security defect wearing the costume of a staleness bug — and every test in the suite still passes, because none of them combine expiry with a warm cache.

The rule, now in `design.md` §9:

```
cacheTtl = min(defaultTtl, link.remainingLifetime)
```

**What caught it was not the AI and not the review — it was writing T-5 first**, from the `RISK` line of the task definition (`EXECUTION-LOG.md` §2). The task format made "what does subtly-wrong look like" a required field, and answering that field produced the test, and the test produced the rule. That is the argument for the format.

## 2.5 Validation

- Unit tests cover warm-cache resolution, expiry-bounded TTL, Redis failure fallback, and database failure mapping.
- Docker fault injection verified Redis-down fallback to PostgreSQL, PostgreSQL-down warm-cache redirects, and `503` for uncached redirects. It also exposed and led to a fix for transaction-acquisition failures that previously returned `500`.
- The k6 harness measured 4.88 ms warm-cache p95, 7.91 ms cache-miss/PostgreSQL-path p95, 8.74 ms sustained Redis-outage p95 with the circuit breaker, and 6.85 ms after Redis recovery, all with 0% failures. The results pass the 50 ms design target; production claims still require a controlled benchmark environment.

## 2.6 Risks and sign-off

| Risk | Control |
|---|---|
| Cache/DB divergence on delete | After-commit eviction (B-05) + bounded TTL; ordering regression test |
| Redis failure cascades to redirects | Fail-open, T-6; circuit opens after three failures and permits one recovery probe after five seconds |
| Rate limiter degraded by shared Redis | Deliberate fail-closed asymmetry, documented in `design.md` §9 |
| Negative-cache shadowing a new link | TTL kept to 30 s |

**Signed off:** Harika Modali, 2026-08-27 — redirect status/failure handling, after-commit deletion eviction, and cache circuit-breaker recovery reviewed against the 38-test Docker-backed gate and recorded fault injection.

---

# Scenario 3 — Ambiguous: "add analytics"

*Build step 8. The requirement is one sentence and every word of it is a question.*

## 3.1 Requirement understanding — naming the ambiguity

Stated requirement: *"Add analytics so users can see how their links perform."*

The failure mode here is not building it wrong. It is **building something plausible without noticing a decision was made.** Five ambiguities, each with a default that would have been chosen silently:

| # | Ambiguity | Silent default | Why the default is wrong |
|---|---|---|---|
| 1 | What is a "click"? | Every request to `/{code}` | Would count `404`s, expired links, and bot traffic as engagement |
| 2 | Real-time or eventual? | Real-time, by assumption | Forces a synchronous write onto the hot path and breaks NFR-1 |
| 3 | Counter or events? | `clicks++` column | Cannot ever answer "clicks last week"; unrecoverable once chosen |
| 4 | "Unique" visitors — by what? | Raw IP | Creates a PII obligation the feature does not need |
| 5 | Durability of a click | Assumed guaranteed | Determines whether Kafka is required on day one |

**AI was useful for enumeration and useless for recognition.** Asked to design analytics, it produced a competent counter-column implementation with no indication that a question had been decided. Asked *"what is ambiguous in this requirement?"*, it surfaced four of the five. The difference is entirely in the prompt, and knowing to ask the second question is the human contribution. The sanitized reconstructed clarification and implementation prompts are recorded in `EXECUTION-LOG.md` §4.3.

## 3.2 Normalization — decisions with owners

| # | Decision | Rationale | Cost, accepted |
|---|---|---|---|
| 1 | A click is a successful `302` only. User-agent data is retained without bot classification. | Expired/deleted/invalid are not engagement. The prototype preserves queryable request facts without claiming a classifier it does not implement. | Bot traffic remains included unless a later query classifies it |
| 2 | Eventually consistent; `asOf` in every response | Real-time would put a synchronous write on the hot path, violating NFR-4 | Callers see slightly stale numbers — made visible rather than hidden |
| 3 | Per-click event rows, not a counter | Aggregates derive from events; events never derive from aggregates. A counter is a one-way door. | Storage: ~11B rows over 3 years (`design.md` §3) |
| 4 | `HMAC-SHA256(configured_secret, day || ip)` | A secret key protects against database-only offline guessing, while the date prevents direct equality from linking a visitor across days. | Secret compromise permits guessing for known dates; multi-day uniques over-count |
| 5 | Best-effort. Bounded queue, dropped under extreme load. | Analytics loss is acceptable; a redirect outage is not. Dropping beats an OOM. | Unflushed clicks lost on crash — stated plainly, not buried |

**Decision 3 is the one that would have been irreversible.** A counter column is cheap, obvious, and satisfies the requirement as literally stated. It also makes "clicks last week" permanently unanswerable, and by the time someone asks, the data to answer it was never recorded. Ambiguous requirements are dangerous less because they are unclear than because the cheap reading is often a one-way door.

**Decision 5 is the one that exposed a hidden constraint.** Making it forced the question *why is link creation synchronous but click recording is not?* — and the answer (a caller who receives `201` must be able to trust it; nobody's trust depends on a click count) is a durability boundary that runs through the whole system and was never written down until this scenario required it.

## 3.3 Decomposition

```
A-01 click_events schema + Flyway                 (decision 3 embodied)
  └─ A-02 ClickRecorder — bounded queue           (decision 5)
       ├─ A-03 ClickFlusher — @Scheduled batch insert
       ├─ A-04 Hook into redirect path, async      (decision 2 — must not block)
       └─ A-05 StatsService + GET .../stats        (decisions 1 and 4)
```

## 3.4 AI-assisted execution and one rejection

`ClickRecorder` was **rejected outright rather than edited.** The generated implementation used an unbounded `LinkedBlockingQueue` — under sustained load with a stalled flusher, the queue grows until the JVM dies, taking every redirect with it. The design decision was explicit (`design.md` §10: drop under load rather than OOM) and the generated code inverted it. Rewritten by hand: bounded queue, `offer()` returning false, and an internal drop counter.

**Rejected rather than edited**, because the fix would have been the entire decision. The distinction is recorded in `EXECUTION-LOG.md` §3 for exactly this reason.

## 3.5 Validation

- **T-11 is now executed.** `RedirectControllerHotPathTest#redirectStaysFastWhileClickFlushIsStalledOnTheDatabase` blocks a mocked batch write mid-flush and asserts a sequence of 50 redirects returns without waiting for it, while a full queue drops rather than blocks the caller. Written after the fact — this was a documented gap the assignment asked to be closed, not part of the original scenario build — and it directly exercises `RedirectController` and `ClickRecorder`, not just the isolated queue. The first version of this test stubbed `JdbcTemplate.batchUpdate` to return a value typed `int[]`; the real overload returns `int[][]`, so the stub's `ClassCastException` was silently caught by `ClickFlusher`'s own failure-handling path, producing a test that passed for the wrong reason — it measured recovery-after-failure, not a genuinely stalled sink. Caught only by tracing the drop count by hand against the expected queue-capacity arithmetic, not by the assertion itself passing or failing. A small, concrete instance of this document's recurring point: generated test code needs the same scrutiny as generated production code.
- **Queue-saturation unit test** — fills the bounded recorder, then asserts excess events are dropped and the drop counter increments. It verifies the backpressure policy, but does not measure redirect latency.
- **Multi-batch flush tests** — one scheduled invocation drains multiple 500-event batches, up to the default 10,000-event queue bound, while failed database writes restore drained events.
- **`asOf` is asserted** in the stats-service unit test, so eventual consistency is explicit in that response contract.
- **NFR-6 log-redaction assertion is now executed on the redirect path.** `RedirectControllerHotPathTest#neverLogsRawClientAddressOrTargetUrl` attaches a Logback appender around a redirect carrying a marked target URL and synthetic client address, and asserts neither value appears in captured log output or MDC context. Coverage beyond this one call site remains manual observation, not automated — the app has no other call sites that log request data today, so this is a regression guard against one being added carelessly, not proof across the whole codebase.

## 3.6 Known limitations, stated rather than smoothed over

- **`totalClicks` will become wrong** once the 90-day retention window truncates, because the prototype does not build the rollup to `click_daily_agg` (`design.md` §14.2). This is a correctness gap, not a performance one, and it is the largest divergence between the design and the running system.
- **Bot classification is not implemented.** User-agent values remain available for later query-time classification, so current click totals include bot traffic.
- **Multi-day unique counts over-count** returning visitors because date-separated hashes intentionally do not compare equal across days.

**Signed off:** Harika Modali, 2026-08-27 — analytics schema migration and bounded best-effort queue behavior reviewed, including the accepted event-loss limitations.
