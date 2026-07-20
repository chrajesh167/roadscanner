# Provider Integration Service — Overview

## Purpose

`provider-integration-service` is the platform's **sole gateway to external transportation
providers** — FlixBus first, with RedBus, AbhiBus, KSRTC, IntrCity, and future providers addable
without touching this service's business logic. No other RoadScanner service is permitted to call
a provider's API directly (see "Future Design Rules" in the request that created this service);
this is the one place provider authentication, session management, request/response mapping,
error translation, and resilience live.

## Where It Sits

- A new 12th service — see `docs/architecture/high-level-design.md` §3 (service inventory) and
  §12 (extensibility). Not present in this platform's original Phase 1 documentation; added to
  isolate volatile, external-integration-specific concerns from `booking-service`,
  `search-service`, and `inventory-service`, the same way `payment-service` isolates payment
  gateway integration.
- Sits behind `api-gateway` for platform consistency, but its actual callers are other backend
  services (`booking-service`, `search-service`, `inventory-service`), not end-user clients — see
  `boundaries.md`.
- Publishes three audit events (`ProviderUnavailable`, `ProviderRecovered`, `SessionExpired`) and
  consumes none — see `events-published.md`/`events-consumed.md`.

## Bounded Context

**In:** provider authentication and session lifecycle, search/seat-map/seat-block/booking-
confirmation/ticket-download pass-through, provider capability discovery, provider health
monitoring, resilience (circuit breaking, retry, rate limiting, bulkheading) around every
outbound provider call.

**Out:** RoadScanner user identity/authentication (`auth-service`), booking state
(`booking-service`), inventory/seat-hold state (`inventory-service`), payments
(`payment-service`), notifications (`notification-service`), the platform's own search index
(`search-service`). See `responsibilities.md` for the full breakdown.

## Relationship to Other Services

`provider-integration-service` is a **translation and resilience boundary**, not a read-model
aggregator like `search-service` or a state-owning service like `booking-service`. It holds
exactly the state needed to keep a provider interaction working (sessions, provider config,
health, audit trail — see `data-ownership.md`) and passes through everything else (trips, seat
maps, reservations, bookings, tickets) without persisting it, because it owns none of that data by
design — the calling service does.

## Design Principles Carried From the Platform Level

- Own database, no shared schema, no cross-service joins (`database-ownership.md`).
- Health, metrics, and OpenAPI exposed from the first deployable commit (NFR-15), same as every
  other service.
- Stateless request handling — session/token state lives in Postgres/Redis, never in process
  memory, so any instance can serve any request (NFR-3).
- Resilience at the platform's one true external-network boundary (`high-level-design.md` §11)
  — this service is where "a degraded downstream service doesn't cascade" is enforced for every
  provider integration, via Resilience4j circuit breakers/retries/bulkheads/rate limiters.

## Documents in This Directory

| Document | Covers |
|---|---|
| `responsibilities.md` | Explicit responsibilities, non-responsibilities |
| `boundaries.md` | Deeper reasoning on the harder boundary calls |
| `domain-model.md` | The domain concepts this service holds and their invariants |
| `use-cases.md` | Every inbound-port use case |
| `sequence-diagrams.md` | The full provider-interaction flow and the health-monitoring flow |
| `data-ownership.md` | What this service's database holds — and, just as importantly, what it deliberately does not |
| `events-published.md` | `ProviderUnavailable`, `ProviderRecovered`, `SessionExpired` |
| `events-consumed.md` | (None — and why that's correct, not an oversight) |
| `api-summary.md` | Internal-only operation categories |
