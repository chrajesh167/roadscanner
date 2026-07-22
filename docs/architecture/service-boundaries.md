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
- **Out of scope:** live seat/trip availability — see `provider-integration-service`. The catalog shape of a first-party operator's trips flows into `inventory-service` via events; nothing about live seat state is ever this service's concern.
- **Why separate from `inventory-service`:** operator data is config-like — it changes rarely (an operator adds a bus, updates a route) — while `inventory-service`'s two ingestion paths (this service's events, plus provider catalog synchronization) need to merge that data with a second, independently-changing source. Merging operator configuration with catalog aggregation would force one service to own two different change cadences and two different supply models.

### `inventory-service`
- **In scope:** catalog and metadata — cities, stations, routes, operators/buses (referenced, not owned), trip metadata, static seat layouts, provider mappings, fare snapshots, catalog synchronization. See `docs/services/inventory-service/` for the full design (corrected by architecture review, 2026-07-22 — this service previously owned live seat holds; it no longer does).
- **Out of scope:** live seat/trip availability, seat holds, seat reservations — see `provider-integration-service`. The booking record itself (who booked, passenger details, payment status) — see `booking-service`.
- **Why separate from `provider-integration-service`:** catalog data is config-like — it changes when a route is added or a fare is revised, rarely and safely eventually-consistent. Live seat state changes on every hold, release, and booking, across every provider, constantly. Merging them would force one service to be tuned for two incompatible load shapes — the same reasoning that separates `operator-service` from transactional data, applied one layer further down the stack now that an external-provider layer exists.
- **Trade-off:** answering "can I book this specific seat" now requires composing two services' answers (catalog shape from `inventory-service`, live state from `provider-integration-service`) — `booking-service` is where that composition happens. Accepted for the same reason the original inventory/booking split was accepted: the alternative is one service tuned for two incompatible jobs.

### `search-service`
- **In scope:** trip search, ranking, filtering — a **read-only aggregator** over data it does not own.
- **Out of scope:** does not own trips, seats, or fares as source-of-truth; its index is a derived read model built from `inventory-service`'s merged-catalog events (not `operator-service` directly — `inventory-service` is the only service with visibility into both first-party and provider-sourced trips).
- **Why:** search needs to scale and be tuned independently for read-heavy, latency-sensitive query patterns (NFR-1) that look nothing like `provider-integration-service`'s per-request, resilience-heavy provider traffic. Denormalizing into a purpose-built read model is the standard way to get both without compromising either.
- **Trade-off:** the search index lags the source-of-truth by however long event propagation (and, for provider-sourced trips, catalog synchronization) takes. Accepted, because search results feeding into a **hold** attempt are re-validated live against `provider-integration-service` (via `booking-service`) at hold time anyway — a stale search result costs a UX retry, not a correctness bug.

### `booking-service`
- **In scope:** the booking lifecycle and state machine (see `booking-flow.md`), ticket generation, and — corrected by architecture review — the client-facing orchestration of seat selection/hold, composing catalog facts from `inventory-service` with live provider actions from `provider-integration-service` (`Inventory → Provider Integration → Payment → Ticket`).
- **Out of scope:** seat/reservation mechanics (delegated to `provider-integration-service`), catalog/metadata mechanics (delegated to `inventory-service`), payment processing (delegated to `payment-service`).
- **Why:** `booking-service` is the orchestrator of an outcome, not the owner of the mechanics behind it — it should never need to understand provider-specific APIs, Redis locking, or payment-gateway integration to do its job.

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
- **In scope:** **live seat availability, seat holds, and seat reservations** (corrected by architecture review, 2026-07-22 — previously misattributed to `inventory-service`), provider authentication/session management, search/seat-map/seat-block/booking-confirmation/ticket-download pass-through, provider capability discovery, provider health monitoring, and every resilience concern (circuit breaking, retry, rate limiting, bulkheading) around external provider calls.
- **Out of scope:** RoadScanner user identity (`auth-service`), booking state (`booking-service`), catalog/metadata (`inventory-service`), payments, notifications, the platform's own search index.
- **Why:** provider integrations are volatile in a way nothing else in this platform is — each provider has its own API shape, auth flow, rate limits, and failure modes, and new providers are added on an ongoing basis. Concentrating that volatility in one service means every other service depends on one stable, canonical contract regardless of how many providers exist behind it. Live seat state belongs here specifically because, for a third-party provider, this service is the only one with an active session and the only one that can ask the provider's own system — the authoritative source — what's actually true right now.
- **Why it's the only service allowed to call a provider directly:** the same reasoning `api-gateway`'s boundary uses in reverse — a provider-specific integration detail leaking into `booking-service` or `inventory-service` would make *them* a second place that detail has to be maintained, and would make swapping or adding a provider a change scattered across services instead of isolated to one adapter package. See `docs/services/provider-integration-service/boundaries.md`.
- **Trade-off:** every provider operation now costs an extra service hop (`booking-service`/`inventory-service` → `provider-integration-service` → the provider) instead of a direct call. Accepted — the isolation is worth it for the same reason `payment-service` isolates payment gateway integration instead of letting `booking-service` call a payment gateway directly.
- **Known gap (architecture review, 2026-07-22):** no inbound port exists yet for cancelling an *already-confirmed* booking with a provider (`ReleaseSeat` only covers a still-blocked reservation) — needed before `booking-flow.md` steps 6–7 (post-confirmation cancellation) can be implemented. See `docs/services/provider-integration-service/` for follow-up.

## Cross-Cutting Boundary Notes

- **No service owns "the traveler" broadly** — identity (`auth-service`), profile (`user-service`), bookings (`booking-service`), payments (`payment-service`), and reviews (`review-service`) each own their own slice. There is deliberately no single "customer 360" service; that view is composed at the edge (`admin-console`, or `analytics-service` for reporting), not centralized as a write-owner.
- **No service is a passthrough to another's database.** Every cross-service data need is either a synchronous API call or a locally-owned, event-derived read model — never a shared table. See `database-ownership.md`.

## Phase 2+ Note

New verticals (Trains, Flights, Hotels, Cabs) each get their own inventory-equivalent and booking-equivalent service, following the same boundary logic as above — never merged into the existing bus-specific services. Shared-concept services (`auth-service`, `user-service`, `payment-service`, `notification-service`, `review-service`, `api-gateway`) are reused unchanged. See `high-level-design.md` §12.

`provider-integration-service` is bus-provider-specific in Phase 1 (its domain model — trips, seat maps, bus seat blocks — is bus-shaped). A future vertical integrating with its own external providers (e.g. a flight-aggregator API) would most likely warrant its own equivalent service rather than overloading this one's bus-shaped domain model, following the same "never merge into an existing vertical-specific service" logic applied above — a decision to make when that vertical is actually planned, not one this document anticipates in code today.
