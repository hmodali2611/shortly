# Execution Log — AI-Assisted Engineering Record

**Companion to:** `design.md` (what was built and why), `SCENARIOS.md` (three worked walkthroughs)
**This document covers:** how AI was used, what it produced, what was kept, what was rejected, and who signed off.
**Cross-reference convention:** `§n` alone always refers to a section of *this* document. References to another document are always written with its filename — `design.md §9`, `SCENARIOS.md §2.4`.

> **Evidence status.** The implementation and local JVM gates are complete. Entries marked ⬜ require human sign-off and are intentionally not claimed. Docker-backed integration, k6, and the optional OWASP scan remain pending because Docker and the vulnerability database were unavailable in the authoring environment. Narrative test IDs below describe scenario acceptance criteria; the executable evidence is the test source and the measured results in §6.

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
| 2 | `ShortCodeGenerator` | Full generation | ❌ → 🔁 | First output used `java.util.Random`. Non-negotiable violation of FR-6 — `Random` is a 48-bit LCG and predictable from prior outputs. Re-prompted with the constraint stated explicitly; second output used `SecureRandom`. **Recorded because this is exactly the failure mode that passes review by looking right.** | T-3, manual read | ⬜ |
| 3 | Flyway `V1__init.sql` | Generation from `design.md` §5 | ✏️ | Emitted `VARCHAR(16)` for `short_code` — faithfully copying the design document's own defect (`design.md` §5 vs `design.md` §8, 32-char aliases). AI reproduced the inconsistency rather than catching it. Fixed in both. **AI does not review your design; it inherits it.** | T-14, migration on fresh container | ⬜ |
| 4 | `UrlSafetyValidator` (SSRF) | Generation + adversarial review | ✏️ | Generated allowlist and private-range checks correctly. Missed `169.254.169.254` as a distinct case and did not handle IPv6-mapped IPv4 (`::ffff:10.0.0.1`) bypass. Second pass, prompted adversarially ("how would you bypass this?"), surfaced both. | T-8 + added bypass cases | ⬜ |
| 5 | Test bodies for T-1…T-25 | Generation from test table | ✏️ | Strong at Testcontainers boilerplate. Weak at concurrency: proposed T-2 used two sequential calls, which does not test the race at all. Rewritten by hand with `CountDownLatch`. | Mutation check: T-2 fails when the unique constraint is dropped | ⬜ |
| 6 | `GlobalExceptionHandler` / RFC 7807 | Full generation | ✅ | Mechanical mapping from the `design.md` §7 status table. Low-risk, high-tedium — the ideal case for delegation. | T-10, error-shape assertions | ⬜ |
| 7 | `ClickRecorder` bounded queue | Generation | ❌ | Proposed an unbounded `LinkedBlockingQueue`. Directly contradicts `design.md` §10's "drop under load rather than OOM." Written by hand instead: bounded queue, `offer()` with drop-and-count. Rejected rather than edited because the fix is the entire design decision. | T-11, drop-counter metric | ⬜ |
| 8 | Javadoc + README | Generation from code + design | ✏️ | Accurate but inflated — described the prototype's guarantees in production terms. Trimmed to match `design.md` §1's scope boundary. **AI defaults to flattering the artifact.** | Manual read against `design.md` §1 | ⬜ |
| 9 | Load-test harness (k6) | Generation | ✏️ | Fine harness; initial script measured only cache-warm reads, which would have produced a flattering and meaningless p95. Added cold-start and mixed read/write phases. | T-12, recorded result | ⬜ |

**Pattern worth naming.** The rejections cluster in one place: entries 2, 7, and the concurrency half of 5 are all cases where the AI produced *idiomatic, compiling, plausible* code that violated a stated non-functional constraint. It was reliable on structure and syntax, and unreliable precisely where the design's reasoning lived. That is the boundary this project treats as the human's responsibility.

---

## 4. Prompt refinement — a real iteration

The SSRF validator (ledger #4), showing why the first prompt was insufficient rather than just showing the final one.

**Attempt 1** — *"Write a Java validator that blocks SSRF in user-supplied URLs."*
Output: scheme check plus a hardcoded list of private CIDR strings compared against the *hostname text*. Blocks `http://10.0.0.1`, fails on `http://internal.example.com` resolving to `10.0.0.1`. Textual matching where resolution was required.

**Attempt 2** — added: *"Resolve the hostname to IP addresses first. Block loopback, private, link-local, and unqualified hosts. Reject if ANY resolved address is blocked."*
Output: structurally right, used `InetAddress.getByName()` — returns only the first address, so a multi-A-record host with one public and one private address passes.

**Attempt 3** — added: *"Use getAllByName and reject if any address fails. List every bypass you can think of against this code."*
Output: correct implementation, plus it surfaced IPv6-mapped IPv4 and the cloud metadata endpoint unprompted.

**Engineer addition, not AI-surfaced:** the DNS-rebinding gap in `design.md` §10 — validation and redirection are different moments and no amount of validation-time checking closes it. The AI hardened what it was pointed at; recognising that the *entire approach* has a time-of-check/time-of-use hole was human work, and it is documented as a known limitation rather than papered over.

**Generalisation applied to later prompts:** state the threat model, not the feature. "Block SSRF" is a label; "reject if any resolved address is in these ranges, and here is the attacker's capability" is a specification.

---

## 5. Secure AI usage

| Rule | Practice |
|---|---|
| No secrets in prompts | No API keys, connection strings, or `.env` contents. Config discussed by shape (`spring.datasource.url`), never by value. |
| No proprietary data | Prototype only; no employer code, internal identifiers, or customer data. |
| Dependencies verified, not trusted | Every AI-suggested dependency checked to exist at the named version and to be current. AI hallucinates plausible artifact coordinates, and a typosquatted coordinate is a supply-chain compromise. OWASP dependency-check enforces this in `mvn verify`. |
| Generated code is untrusted input | No AI output executed before reading. No copy-paste of shell commands without inspection. |
| Licence hygiene | Generated code reviewed for verbatim recognisable third-party sources. |

---

## 6. Quality gates and sign-off

Evidence from the latest local `./mvnw verify` run:

| Gate | Result | Status |
|---|---|---|
| Build/package | Spring Boot JAR built successfully | ✅ |
| Tests | 27 run, 0 failed, 0 errors, 1 Docker-dependent skip | ✅ local / ⬜ Docker |
| Format | Spotless: 42 Java files clean | ✅ |
| Static analysis | SpotBugs: 0 warnings/errors | ✅ |
| Coverage | JaCoCo critical-service aggregate: 90.8% (148/163); enforced floor 80% | ✅ |
| Dependencies | OWASP dependency-check, CVSS 7 threshold | ⬜ not executed |
| Load smoke | k6 p95 <100 ms script | ⬜ not executed |

GitHub Actions runs `./mvnw --batch-mode verify` on Java 21. Its Docker-capable runner is the intended integration gate; a passing workflow must be captured before submission.

**High-impact changes requiring explicit engineer sign-off before merge**, regardless of gate status: anything in `security/`, any Flyway migration, any change to the code generator, any change to redirect-path failure handling, and any change that alters a documented status code. These are the areas where a passing test suite is weakest evidence — a green build proves the code does what the tests say, and in exactly these areas the risk is that the *tests* encode the wrong belief.

**Standing limitation of the gates.** Coverage measures lines executed, not properties proven. T-2 (the alias race) is the only test in the suite that would fail if its underlying mechanism were removed while coverage stayed identical. The gates catch regression and hygiene; they do not catch a design that is confidently wrong.
