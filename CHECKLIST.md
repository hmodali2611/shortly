# Submission Checklist — AI-Assisted URL Shortener Assignment

Derived from `Assignment AI-Proficient Software Engineer.pdf`. This document translates the
assignment brief into a concrete, checkable requirements list that will drive implementation,
scenario execution, and final submission. Every item here must be satisfiable and demonstrable
in the final deliverable.

---

## 1. Objective (from brief)

Build a working prototype that transforms a requirement into a reviewable engineering outcome
using AI-assisted engineering execution, demonstrating:
- Requirement understanding
- Task decomposition
- Multi-step execution
- Output generation/validation

Focus: **engineer-led execution accelerated by AI**, not autonomous AI orchestration. The engineer
must own every decision, and every AI-generated artifact must be reviewed, edited, or rejected
with rationale.

---

## 2. Product Scope — URL Shortener Service

### 2.1 Functional Requirements (FR)

| ID | Requirement |
|----|-------------|
| FR-1 | `POST /api/v1/links` creates a shortened URL from a long URL. Supports optional custom alias and optional expiry (TTL or absolute datetime). |
| FR-2 | `GET /{shortCode}` redirects with HTTP `302` to the original long URL. |
| FR-3 | `GET /api/v1/links/{shortCode}/stats` returns analytics: total clicks, first/last click timestamp, referrer breakdown, user-agent/device breakdown. |
| FR-4 | `GET /api/v1/links/{shortCode}` returns metadata for a short URL (owner-safe fields only; no PII leakage). |
| FR-5 | `DELETE /api/v1/links/{shortCode}` soft-deletes a short URL while preserving analytics integrity. |
| FR-6 | Short codes are collision-resistant (base62 encoding of an auto-increment/sequence, or hash + retry-on-collision). |
| FR-7 | Custom aliases must be validated (charset, length, reserved-word/profanity/collision checks). |
| FR-8 | Expired or deactivated short URLs return `410 Gone` (not a silent 404) on redirect attempts. |
| FR-9 | Health (`/healthz`) and readiness (`/readyz`) endpoints for the service. |
| FR-10 | OpenAPI/Swagger schema documents all endpoints, request/response shapes, and error codes. |

### 2.2 Non-Functional / Reliability Requirements (NFR)

| ID | Requirement |
|----|-------------|
| NFR-1 | Redirect path (`GET /{shortCode}`) must be low-latency: cache-backed (Redis) cache-aside reads in front of the primary DB. |
| NFR-2 | Rate limiting on write endpoints (`POST /api/v1/links`) prevents abuse. |
| NFR-3 | Input validation and SSRF/URL-safety guardrails: reject `javascript:`/`data:`/`file:` schemes, reject loopback/link-local/private-IP targets unless explicitly allow-listed. |
| NFR-4 | Click tracking must not block the redirect response (async/best-effort write, e.g. fire-and-forget or queued). |
| NFR-5 | Service must run fully locally via a single command (`docker compose up`), with no external paid dependencies. |
| NFR-6 | Structured logging (JSON) with request correlation IDs; no sensitive data (full URLs with secrets/tokens) logged in plaintext beyond what's necessary. |
| NFR-7 | Graceful error handling: all errors return consistent JSON error envelopes with appropriate HTTP status codes. |
| NFR-8 | Config via environment variables (12-factor); no secrets committed to the repo. |
| NFR-9 | Database migrations are version-controlled and repeatable (not manual schema edits). |

### 2.3 Security Requirements (mapped to OWASP Top 10, per operating instructions)

| ID | Requirement |
|----|-------------|
| SEC-1 | Injection: use parameterized queries/ORM; no string-concatenated SQL. |
| SEC-2 | SSRF: validate/deny-list redirect targets as in NFR-3. |
| SEC-3 | Broken access control: stats/delete endpoints require an owner/API-key check (even if minimal auth model for a prototype). |
| SEC-4 | Rate limiting + input size limits to mitigate DoS on create endpoint. |
| SEC-5 | Dependency scan (`npm audit` / `pip-audit` or equivalent) run and results recorded as a quality gate. |
| SEC-6 | No secrets/API keys committed; `.env.example` provided instead of `.env`. |

---

## 3. AI-Assisted Engineering Process Requirements (the "critical differentiator")

These are process requirements, not code requirements — they must be **documented artifacts**,
not just practiced silently.

| ID | Requirement |
|----|-------------|
| AI-1 | For each task, define: intent, constraints, acceptance criteria, technical context — *before* invoking AI. |
| AI-2 | Use disciplined, iterative prompting (not one-shot "build this"); capture prompt evolution where meaningful. |
| AI-3 | Maintain a traceability log: what AI generated, what was accepted as-is, what was edited (and why), what was rejected (and why). |
| AI-4 | Apply quality gates before accepting AI output: static analysis/lint, tests passing, security scan, basic performance sanity check. |
| AI-5 | Enforce secure AI usage: never paste secrets/PII into prompts; treat AI output as untrusted input requiring review (esp. for security-sensitive code — auth, validation, SSRF guards). |
| AI-6 | High-impact changes (schema migrations, security guardrails, deletion logic) require explicit human sign-off recorded in the traceability log. |
| AI-7 | Engineer retains explicit ownership: final say on correctness, maintainability, production-readiness for every artifact. |

---

## 4. Three Required Scenarios

Each scenario must independently demonstrate: **decomposition → execution → validation**.

### 4.1 Greenfield Scenario
- Requirement: build the core URL shortener service (FR-1..FR-10, NFR-1..NFR-9) from nothing.
- Must show: initial requirement normalization, task breakdown with dependencies/sequencing,
  AI-assisted implementation, resulting tests, and validation against acceptance criteria.

### 4.2 Brownfield Scenario
- Requirement: a scoped enhancement/refactor/bug-fix applied to the greenfield codebase.
- Must show: identification of impacted modules/services/data flows (codebase reasoning),
  a decomposition specific to the change, execution with tests updated/added, and validation
  that existing behavior is not regressed.
- Candidate (pending confirmation): add per-URL expiration + background cleanup job, **or**
  add a caching layer/refactor storage access for the redirect hot path.

### 4.3 Ambiguous Scenario
- Requirement: a deliberately underspecified ask requiring the engineer to resolve ambiguity
  before decomposing.
- Must show: explicit list of ambiguities identified, assumptions made (with rationale),
  a normalized/clarified requirement statement, then decomposition → execution → validation.
- Candidate (pending confirmation): "make click analytics available in near real-time" (no
  definition of latency, scale, or delivery mechanism given).

---

## 5. Deliverables Checklist

| ID | Deliverable |
|----|-------------|
| D-1 | Working prototype, runnable end-to-end via a single documented command. |
| D-2 | `ARCHITECTURE.md`: components, tools used, execution approach, control flow diagram, key decisions + trade-offs. |
| D-3 | Three scenario write-ups (`docs/scenario-greenfield.md`, `docs/scenario-brownfield.md`, `docs/scenario-ambiguous.md`), each with decomposition/execution/validation sections. |
| D-4 | `README.md` with setup instructions (prereqs, run command, how to exercise the API). |
| D-5 | Testing approach doc: unit + integration test strategy, coverage summary, known limitations, trade-offs. |
| D-6 | `docs/ai-traceability.md`: AI usage log per AI-1..AI-7. |
| D-7 | `docs/risks-and-validation.md`: risks/trade-offs/failure scenarios, guardrails, quality gate results. |
| D-8 | Final engineering summary (can be part of README or a standalone doc): plan/rationale, artifacts index, risks/trade-offs/validation summary, assumptions, limitations. |
| D-9 | OpenAPI schema file (`openapi.yaml`) for the API surface. |
| D-10 | Automated tests (unit + integration) committed and passing in CI or via a documented local command. |

---

## 6. Evaluation Criteria Mapping (self-check before submission)

| Evaluation Criterion (from brief) | Where it's demonstrated |
|---|---|
| Effectiveness of AI-assisted engineering execution | D-6 traceability log + AI-1..AI-7 practiced throughout |
| Architecture/system design quality | D-2 ARCHITECTURE.md |
| Depth of decomposition and execution quality | D-3 scenario docs |
| Realism/quality of outputs | D-1 working prototype, D-9 OpenAPI, D-10 tests |
| Validation and risk management rigor | D-7 risks-and-validation.md |
| Clarity and defensibility of decisions | D-2, D-3, D-8 rationale sections |
| Core engineering principles (modular, testable, reliable, secure, scalable, safe change mgmt) | FR/NFR/SEC sections + code structure |
| Engineering judgment | Ambiguous scenario (4.3) + trade-off call-outs throughout |

---

## 7. Decisions

- [x] **Stack**: Java 21, Spring Boot 3.5, PostgreSQL 16, Redis 7, and Maven.
- [x] **Infra depth**: local Docker Compose prototype; cloud deployment is out of scope.
- [x] **Brownfield scenario**: introduce Redis caching on the tested redirect path.
- [x] **Ambiguous scenario**: clarify and implement click analytics.
- [ ] **Repository**: connect the final GitHub remote and verify the submitted link.

## 8. Pre-Submission Gate

- [ ] Application starts from a clean clone using the commands in `README.md`.
- [ ] `docker compose up --build -d` starts the application, PostgreSQL, and Redis successfully.
- [x] `./mvnw verify` passes local tests, formatting, static analysis, packaging, and enforced coverage (Docker integration skipped).
- [ ] `./mvnw spring-boot:run` starts the API without manual setup.
- [ ] Create, redirect, stats, delete, health, and readiness flows are exercised.
- [x] Checked-in OpenAPI contract and generated Swagger paths match the implemented API.
- [x] No production secrets are included; `.env.example` contains placeholders and local defaults are explicitly development-only.
- [x] `EXECUTION-LOG.md` distinguishes measured evidence from pending Docker, load, security-scan, and human-sign-off work.
- [ ] High-impact schema, security, deletion, and redirect changes have human sign-off.
- [x] Known limitations and local quality-gate results reflect the final implementation.
- [x] Deliverables D-1 through D-10 are represented; the three scenarios are intentionally combined in `SCENARIOS.md` and traceability is in `EXECUTION-LOG.md`.

---

## 9. Time Budget Mapping (target 6-8 hrs, hard cap 10 hrs)

| Phase | Hours |
|---|---|
| Requirements + architecture + task decomposition (this doc + ARCHITECTURE.md) | 0.5–1 |
| Greenfield build (core APIs, DB, cache, tests) | 2.5–3 |
| Brownfield scenario | 1–1.5 |
| Ambiguous scenario | 1–1.5 |
| Quality gates (lint/test/security/perf) + traceability + risk docs | 1 |
| Final review, README, packaging | 0.5–1 |
