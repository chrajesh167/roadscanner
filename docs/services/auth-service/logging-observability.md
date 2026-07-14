# Auth Service — Logging & Observability

## Logging

Structured logs, per `.claude/CODING_STANDARDS.md`, with the platform-wide correlation ID (`docs/architecture/high-level-design.md` §9) attached to every entry so a single request — including a failed one — is traceable end-to-end.

**What gets logged:** authentication attempts (success/failure, by identifier and outcome — not credentials), token issuance, refresh, and revocation events, account lockouts, password-reset requests and completions, role assignments.

**What is never logged, under any circumstance:** raw passwords, raw access tokens, raw refresh tokens, password hashes, reset-token values. This is an absolute rule, not a "usually" — a leaked log line is a leaked credential. Log entries reference tokens/credentials only by non-reversible identifiers (e.g., a token's database row ID, not its value).

## Audit Trail

Security-relevant events — login, logout, password reset, role change, token-reuse detection — are treated as an **audit trail**, not merely ephemeral application logs: same structured log stream, but tagged for longer retention, since these are the events most likely to matter to a future security investigation or compliance question. This is a retention/handling distinction, not a separate logging system.

## Metrics (Prometheus)

| Metric | Type | Why it matters |
|---|---|---|
| Login success/failure rate | Counter | Baseline health; a sudden failure spike is often the first signal of an outage or an attack |
| Registration rate | Counter | Product/growth signal |
| Token refresh rate | Counter | Expected background load; a discontinuity often indicates a client-side bug |
| Token reuse detected | Counter | **Security signal** — a non-zero rate warrants investigation, a sustained rate warrants paging |
| Account lockout count | Counter | Brute-force indicator, especially spiking against a single identifier |
| Password reset request rate | Counter | Can indicate a phishing campaign if it spikes for unrelated accounts |
| Request latency (p50/p95/p99) per operation | Histogram | Standard service health |
| Active sessions | Gauge | Capacity/operational signal, optional for v1 |

The token-reuse and lockout metrics are called out specifically because they are **security** signals, not just operational ones — this service's dashboards should be read differently from a typical CRUD service's.

## Health Endpoint

Reports connectivity to Postgres (durable store) and Redis (revocation cache), and availability of signing-key material — all three are required for the service to function correctly, and a health check that only pings the database would miss a broken key-loading configuration.

## Tracing

Correlation ID propagated from `api-gateway` (per platform convention) through every log line and, since `auth-service` has no Kafka events, through its synchronous calls only (e.g., the role-assignment call from `operator-service`).
