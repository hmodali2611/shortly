# URL Shortener

**Author:** Harika Modali

A Java 25/Spring Boot 3.5 URL shortener with PostgreSQL persistence, Redis cache-aside redirects, anonymous management APIs, and asynchronous click analytics.

## Quick Start

Prerequisite: Docker Engine with Compose. Docker Buildx is recommended; Compose can fall back to the classic builder when it is unavailable.

```bash
docker compose up --build
```

The web app is available at `http://localhost:8080`, and no authentication is required. Override `ANALYTICS_IP_HASH_SECRET` outside local development. See `.env.example` for the primary settings.

Useful URLs: dashboard at `http://localhost:8080/dashboard.html`, Swagger at `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at `http://localhost:8080/v3/api-docs`, liveness at `http://localhost:8080/healthz`, and readiness at `http://localhost:8080/readyz`.

## API Walkthrough

```bash
curl -i http://localhost:8080/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"targetUrl":"https://example.com","customAlias":"example-link"}'

curl -i http://localhost:8080/example-link

curl -i http://localhost:8080/api/v1/links/example-link/stats

curl -i -X DELETE http://localhost:8080/api/v1/links/example-link
```

## Verification

The reproducible, Docker-only verification path is:

```bash
docker compose --profile test run --rm tests
docker compose --profile test run --rm security
```

The test command runs unit/integration tests, Spotless, SpotBugs, and an enforced 80% aggregate line-coverage floor over critical service classes. It provides Docker to Testcontainers and currently completes 49 tests with zero skips. The security command runs OWASP Dependency-Check and requires internet access to update vulnerability data; an NVD API key is recommended for faster cold updates.

To run the same commands directly on the host, install Java 25 and ensure Testcontainers can access the active Docker socket:

```bash
./mvnw verify
./mvnw -Psecurity dependency-check:check
```

The Docker-backed suites fail rather than silently skip when Testcontainers cannot reach Docker. This commonly affects host-side runs with non-default runtimes such as Colima; use the Docker-only command above or configure Testcontainers for that runtime. CI runs `verify` on Java 25.

Optional load smoke test (requires k6 and a running service):

```bash
k6 run -e BASE_URL=http://localhost:8080 load/url-shortener.js
k6 run -e BASE_URL=http://localhost:8080 load/create-read.js
```

The second workload creates 100 unique links and performs exactly 10,000 redirect reads.

The Redis cache circuit opens after three consecutive failures and retries with one probe after five seconds. `CACHE_CIRCUIT_FAILURE_THRESHOLD` and `CACHE_CIRCUIT_OPEN_DURATION` override those defaults.

## Engineering Artifacts

Recommended reviewer path; each artifact owns one concern:

1. [ARCHITECTURE.md](ARCHITECTURE.md): concise implementation orientation
2. [design.md](design.md): authoritative requirements, decisions, trade-offs, and scale path
3. [SCENARIOS.md](docs/SCENARIOS.md): greenfield, brownfield, and ambiguous-requirement execution
4. [EXECUTION-LOG.md](docs/EXECUTION-LOG.md): AI prompts, dispositions, provenance, and human sign-off
5. [TESTING.md](TESTING.md): authoritative measured test, coverage, security, and performance evidence
6. [RISKS-AND-VALIDATION.md](docs/RISKS-AND-VALIDATION.md): authoritative residual-risk register
7. [openapi.yaml](openapi.yaml): checked-in API contract

## Current Limitations

This is a single-region prototype. All management endpoints are intentionally unauthenticated, so anyone who knows a short code can read its metadata and analytics or delete it. Click delivery is intentionally best-effort, Redis rate limiting is per-instance, and analytics retention/rollups are not implemented.

**Authentication was evaluated, not overlooked.** An earlier iteration implemented a shared static API key, checked with a constant-time comparison and hashed into an anonymous "owner" reference stored per link (`ApiKeyAuthenticator`, `OwnerKeyHasher`, and an `owner_key_id` column). It was removed — a single shared secret can't express per-caller ownership, and embedding it in the dashboard to call the API would have exposed it to every visitor, which defeats the point of having it. That code is gone; the schema change that removed it is `V3__remove_link_ownership.sql`, and its rationale is preserved in `EXECUTION-LOG.md`. The scoped-back decision was full anonymous access for this prototype, with the production path documented instead of half-built: issue a unique, revocable bearer token after real user authentication, store only its hash or an identity reference, and enforce link ownership server-side on every metadata, analytics, and delete call (`ARCHITECTURE.md`, `design.md` §10). Building that properly needs real user accounts, which is out of scope here — the trade-off is stated, not hidden.