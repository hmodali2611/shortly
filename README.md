# URL Shortener

**Author:** Harika Modali

A Java 25/Spring Boot 3.5 URL shortener with PostgreSQL persistence, Redis cache-aside redirects, anonymous management APIs, and asynchronous click analytics.

## Quick Start

Prerequisite: Docker with Compose.

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

```bash
./mvnw verify
docker compose --profile test run --rm tests
./mvnw -Psecurity dependency-check:check
```

`verify` runs unit/integration tests, Spotless, SpotBugs, and an enforced 80% aggregate line-coverage floor over critical service classes. The Compose test command provides Docker to Testcontainers and currently completes 49 tests with zero skips. CI runs `verify` on Java 25.

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
3. [SCENARIOS.md](SCENARIOS.md): greenfield, brownfield, and ambiguous-requirement execution
4. [EXECUTION-LOG.md](EXECUTION-LOG.md): AI prompts, dispositions, provenance, and human sign-off
5. [TESTING.md](TESTING.md): authoritative measured test, coverage, security, and performance evidence
6. [RISKS-AND-VALIDATION.md](RISKS-AND-VALIDATION.md): authoritative residual-risk register
7. [openapi.yaml](openapi.yaml): checked-in API contract

## Current Limitations

This is a single-region prototype. All management endpoints are intentionally unauthenticated, so anyone who knows a short code can read its metadata and analytics or delete it. Production use requires issued user tokens and ownership authorization. Click delivery is intentionally best-effort, Redis rate limiting is per-instance, and analytics retention/rollups are not implemented.