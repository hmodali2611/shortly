# Risks and Validation

| Risk | Current control | Residual limitation |
|---|---|---|
| Unsafe redirect target / SSRF | Scheme, hostname, and resolved-address validation | DNS rebinding remains possible between validation and later navigation |
| Deleted or expired link served from cache | Eviction on delete; TTL capped by remaining lifetime | Best-effort eviction relies on bounded TTL during Redis failures |
| Redis outage | Redirect reads fail open to PostgreSQL | The prototype rate limiter fails open when Redis is unavailable |
| Database outage | Cached redirects continue; uncached reads return `503` | Management and analytics queries require PostgreSQL |
| Analytics overload | Bounded nonblocking queue drops events instead of exhausting memory | Click totals are best-effort and may lose events on crash/overload |
| Visitor privacy | Daily HMAC of source IP; no raw IP persisted | Secret rotation and retention operations are deployment responsibilities |
| Unbounded analytics growth | Scale design specifies partitions and rollups | Archival cleanup/rollups are not implemented in this prototype |
| Dependency vulnerabilities | Optional OWASP CVSS 7 gate | Scan still requires execution in a networked environment |

Validated locally: unit tests, format, static analysis, packaging, and enforced critical-service coverage. Pending Docker-capable validation: Compose startup, Testcontainers integration flow, and k6 latency thresholds.
