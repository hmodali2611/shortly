# URL Shortener

A Java 21/Spring Boot 3.5 URL shortener with PostgreSQL persistence, Redis cache-aside redirects, owner-scoped management APIs, and asynchronous click analytics.

## Quick Start

Prerequisite: Docker with Compose.

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`. Local Compose uses the development API key `dev-key`; override `APP_API_KEYS` and `ANALYTICS_IP_HASH_SECRET` outside local development. See `.env.example` for the primary settings.

Useful URLs: dashboard at `http://localhost:8080/dashboard.html`, Swagger at `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at `http://localhost:8080/v3/api-docs`, liveness at `http://localhost:8080/healthz`, and readiness at `http://localhost:8080/readyz`.

## API Walkthrough

```bash
curl -i http://localhost:8080/api/v1/links \
  -H 'Authorization: Bearer dev-key' \
  -H 'Content-Type: application/json' \
  -d '{"targetUrl":"https://example.com","customAlias":"example-link"}'

curl -i http://localhost:8080/example-link

curl -i http://localhost:8080/api/v1/links/example-link/stats \
  -H 'Authorization: Bearer dev-key'

curl -i -X DELETE http://localhost:8080/api/v1/links/example-link \
  -H 'Authorization: Bearer dev-key'
```

## Verification

```bash
./mvnw verify
docker compose --profile test run --rm tests
./mvnw -Psecurity dependency-check:check
```

`verify` runs unit/integration tests, Spotless, SpotBugs, and an enforced 80% aggregate line-coverage floor over critical service classes. Testcontainers integration tests require Docker and skip when it is unavailable. CI runs `verify` on Java 21 with Docker available.

Optional load smoke test (requires k6 and a running service):

```bash
k6 run -e BASE_URL=http://localhost:8080 -e API_KEY=dev-key load/url-shortener.js
```

## Engineering Artifacts

- [design.md](design.md): detailed design, requirement diagrams, trade-offs, and 100M-user scale path
- [ARCHITECTURE.md](ARCHITECTURE.md): concise implementation architecture
- [SCENARIOS.md](SCENARIOS.md): greenfield, brownfield cache, and ambiguous analytics scenarios
- [EXECUTION-LOG.md](EXECUTION-LOG.md): AI-assisted execution and validation record
- [TESTING.md](TESTING.md): test strategy and measured evidence
- [RISKS-AND-VALIDATION.md](RISKS-AND-VALIDATION.md): limitations and operational risks
- [openapi.yaml](openapi.yaml): checked-in API contract

## Current Limitations

This is a single-region prototype. Click delivery is intentionally best-effort, Redis rate limiting is per-instance, and analytics retention/rollups are not implemented. Docker execution and the optional OWASP scan were unavailable in the authoring environment; CI is the executable Docker-backed gate.