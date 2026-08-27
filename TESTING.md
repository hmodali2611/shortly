# Testing Approach

## Automated Gates

`./mvnw verify` runs JUnit 5 unit tests, the Docker-aware Testcontainers integration test, Spotless, SpotBugs, and JaCoCo. JaCoCo enforces at least 80% aggregate line coverage over the critical management, security, redirect, and analytics services.

Latest local run:

- Build: passed
- Tests: 27 run, 0 failures, 0 errors, 1 skipped because Docker was unavailable
- Static analysis: 0 SpotBugs warnings/errors
- Formatting: 42 Java files clean
- Critical-service line coverage: 90.8% (148/163), independently measured from the JaCoCo report

The tests cover URL safety, authentication, aliases, generated-code collision retry, create/metadata/delete behavior, cache failure fallback, expiry-bounded cache TTL, queue saturation, and stats aggregation. The integration test exercises create, redirect, metadata, stats, deletion, and health against PostgreSQL/Redis containers when Docker is present.

## Remaining Validation

The local authoring host has no Docker CLI, so the Testcontainers case, full Compose startup, and k6 harness have not been executed locally. GitHub Actions runs the same Maven verification on an Ubuntu Docker-capable runner. The optional OWASP gate is `./mvnw -Psecurity dependency-check:check`; no result is claimed until it runs with vulnerability-database access.
