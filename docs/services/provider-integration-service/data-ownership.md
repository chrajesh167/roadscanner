# Data Ownership

## What This Service's Database Holds

Four tables, all exclusively owned and written by this service (`docs/architecture/database-ownership.md`):

| Table | Purpose |
|---|---|
| `provider_configurations` | Which providers exist, whether enabled, declared capabilities — seeded via Flyway, read-only from application code |
| `provider_sessions` | Durable session state — the source of truth `TokenCache` (Redis) sits in front of |
| `audit_records` | Durable trail of every `ProviderUnavailable`/`ProviderRecovered`/`SessionExpired` event |
| `provider_health` | One row per provider, current health state and failure streak |

## What This Service Deliberately Does Not Store

Search results, seat maps (beyond a short-TTL Redis cache), seat reservations, booking
confirmations, and tickets are **never persisted** — they are pass-through responses from the
provider, mapped into RoadScanner's canonical model and returned once. See `boundaries.md` for
why: this service owns no inventory or booking state by design, and there is no query this
service itself ever needs to answer against that data. Callers (`booking-service`,
`inventory-service`) that need to remember a reservation or booking do so in their own database,
against their own schema, keyed by the identifiers this service returns.

## Redis (Derived, Expendable)

- `provider:token:{sessionId}` — active session tokens, TTL = token expiry
- `provider:capabilities:{providerType}` — capability metadata, ~1h TTL
- `provider:seatmap:{providerType}:{providerTripId}` — seat maps, ~30s TTL

Every Redis key here is a disposable copy or short-lived cache — if Redis is flushed, this
service degrades in latency (a cache miss falls through to Postgres or a live provider call), not
correctness, matching `docs/architecture/high-level-design.md` §7.
