# Service Boundaries

This document defines each service's bounded context precisely — what it owns, what it explicitly does not own, and why the boundary is drawn where it is. It refines `high-level-design.md` §3 with the reasoning behind the harder boundary calls, since those are exactly the decisions that are expensive to reverse once services are built.

## Boundary Principle

A service exists because it has **one reason to change** that no other service shares. If two services would need to change together for most feature requests, they're drawn wrong. Boundaries below follow business subdomains (DDD), not technical layers — there is no generic "data service" or "API layer" service.

## Per-Service Boundaries

### `api-gateway`
- **In scope:** request routing, JWT validation (authentication only), rate limiting, correlation ID injection.
- **Out of scope:** any business logic, any domain data, any authorization decision.
- **Why:** a gateway that starts making business decisions becomes a second place every rule has to be maintained. It stays a pure traffic-shaping layer on purpose.

### `auth-service`
- **In scope:** credentials, login, JWT/refresh-token issuance and revocation, password reset.
- **Out of scope:** profile data (name, contact, preferences) — see `user-service`.
- **Why separate from `user-service`:** identity/credential data has a different security posture, audit requirement, and change cadence than profile data. Keeping it separate means a profile-data bug can never touch credentials, and the credential store can be hardened independently.
- **Trade-off:** assembling a "full user view" (profile + identity) requires composing two services instead of reading one. Accepted — the blast-radius reduction is worth an extra lookup.

### `user-service`
- **In scope:** traveler profile, contact details, saved passengers.
- **Out of scope:** credentials (auth-service), bookings (booking-service).

### `operator-service`
- **In scope:** operator accounts and verification, fleet definitions, route and schedule definitions, fare and cancellation-policy configuration.
- **Out of scope:** live seat/trip availability — see `inventory-service`.
- **Why separate from `inventory-service`:** operator data is config-like — it changes rarely (an operator adds a bus, updates a route) — while inventory data changes constantly (every hold, every booking, every cancellation touches it). Merging a low-frequency configuration store with a high-frequency transactional one would force one service to be tuned for two very different write profiles.

### `inventory-service`
- **In scope:** trip instances, seat maps, live seat availability, seat holds (with Redis-backed TTL locking — see `seat-locking-flow.md`).
- **Out of scope:** the booking record itself (who booked, passenger details, payment status) — see `booking-service`.
- **Why separate from `booking-service`:** "is this seat available right now" and "what did this traveler book" are different questions with different access patterns, different contention profiles, and different retention needs (a seat's availability is transient/current-state; a booking is a permanent audit record). Splitting them means the highest-contention code path (seat holds) is isolated from the highest-retention data (booking history).
- **Trade-off:** every booking now requires coordination between two services (hold in `inventory-service`, record in `booking-service`) instead of one atomic write. Accepted — see `booking-flow.md` for how this coordination is designed to stay correct.

### `search-service`
- **In scope:** trip search, ranking, filtering — a **read-only aggregator** over data it does not own.
- **Out of scope:** does not own trips, seats, or fares as source-of-truth; its index is a derived read model built from `operator-service` and `inventory-service` events.
- **Why:** search needs to scale and be tuned independently for read-heavy, latency-sensitive query patterns (NFR-1) that look nothing like `inventory-service`'s write-heavy hold/release traffic. Denormalizing into a purpose-built read model is the standard way to get both without compromising either.
- **Trade-off:** the search index lags the source-of-truth by however long event propagation takes (typically sub-second, but not zero). Accepted, because search results feeding into a **hold** attempt are re-validated by `inventory-service` at hold time anyway — a stale search result costs a UX retry, not a correctness bug.

### `booking-service`
- **In scope:** the booking lifecycle and state machine (see `booking-flow.md`), ticket generation.
- **Out of scope:** seat allocation mechanics (delegated to `inventory-service`), payment processing (delegated to `payment-service`).
- **Why:** `booking-service` is the orchestrator of an outcome, not the owner of the mechanics behind it — it should never need to understand Redis locking or payment-gateway integration to do its job.

### `payment-service`
- **In scope:** payment initiation, gateway integration, refunds, an internal ledger of transactions.
- **Out of scope:** the decision of *whether* a refund is owed (that's a `booking-service`/`operator-service` policy decision) — `payment-service` executes refunds it's told to execute, it doesn't decide cancellation policy.

### `notification-service`
- **In scope:** templated delivery of email/SMS/push, triggered by events from other services.
- **Out of scope:** deciding *when* a traveler should be notified about a domain event — that decision is expressed by the event itself (e.g., `BookingConfirmed`), not hardcoded in `notification-service`. This keeps notification triggers defined at the source of truth, not duplicated in a delivery service.

### `analytics-service`
- **In scope:** consuming the platform's event stream for reporting/BI. Read-only from the rest of the platform's point of view — it never calls another service synchronously and is never depended on synchronously by another service (NFR-8).

### `review-service`
- **In scope:** ratings and reviews, tied to a verified completed booking.
- **Out of scope:** does not own booking or trip data — it validates against `booking-service` at submission time rather than duplicating booking state.

### `provider-integration-service`
- **In scope:** provider authentication/session management, search/seat-map/seat-block/booking-confirmation/ticket-download pass-through, provider capability discovery, provider health monitoring, and every resilience concern (circuit breaking, retry, rate limiting, bulkheading) around external provider calls.
- **Out of scope:** RoadScanner user identity (`auth-service`), booking state (`booking-service`), inventory/seat-hold state for RoadScanner's own operators (`inventory-service`), payments, notifications, the platform's own search index.
- **Why:** provider integrations are volatile in a way nothing else in this platform is — each provider has its own API shape, auth flow, rate limits, and failure modes, and new providers are added on an ongoing basis. Concentrating that volatility in one service means every other service depends on one stable, canonical contract regardless of how many providers exist behind it.
- **Why it's the only service allowed to call a provider directly:** the same reasoning `api-gateway`'s boundary uses in reverse — a provider-specific integration detail leaking into `booking-service` or `inventory-service` would make *them* a second place that detail has to be maintained, and would make swapping or adding a provider a change scattered across services instead of isolated to one adapter package. See `docs/services/provider-integration-service/boundaries.md`.
- **Trade-off:** every provider operation now costs an extra service hop (`booking-service`/`inventory-service` → `provider-integration-service` → the provider) instead of a direct call. Accepted — the isolation is worth it for the same reason `payment-service` isolates payment gateway integration instead of letting `booking-service` call a payment gateway directly.

## Cross-Cutting Boundary Notes

- **No service owns "the traveler" broadly** — identity (`auth-service`), profile (`user-service`), bookings (`booking-service`), payments (`payment-service`), and reviews (`review-service`) each own their own slice. There is deliberately no single "customer 360" service; that view is composed at the edge (`admin-console`, or `analytics-service` for reporting), not centralized as a write-owner.
- **No service is a passthrough to another's database.** Every cross-service data need is either a synchronous API call or a locally-owned, event-derived read model — never a shared table. See `database-ownership.md`.

## Phase 2+ Note

New verticals (Trains, Flights, Hotels, Cabs) each get their own inventory-equivalent and booking-equivalent service, following the same boundary logic as above — never merged into the existing bus-specific services. Shared-concept services (`auth-service`, `user-service`, `payment-service`, `notification-service`, `review-service`, `api-gateway`) are reused unchanged. See `high-level-design.md` §12.

`provider-integration-service` is bus-provider-specific in Phase 1 (its domain model — trips, seat maps, bus seat blocks — is bus-shaped). A future vertical integrating with its own external providers (e.g. a flight-aggregator API) would most likely warrant its own equivalent service rather than overloading this one's bus-shaped domain model, following the same "never merge into an existing vertical-specific service" logic applied above — a decision to make when that vertical is actually planned, not one this document anticipates in code today.
