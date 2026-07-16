# Search Service — Responsibilities

## Responsibilities

- **Trip search** — given origin, destination, and travel date, return the set of bookable trips (FR-2.1).
- **Filtering & sorting** — price, departure time, duration, operator rating, bus type (FR-2.3), applied as parameters of the same search operation, not separate endpoints — see `api-summary.md`.
- **Ranking** — ordering results by relevance/default sort when the caller hasn't specified one; the concrete ranking algorithm is an implementation decision, not an architectural one (see `use-cases.md`).
- **Result composition** — a search result row shows operator, times, duration, bus type, fare, live seat availability, and aggregate rating (FR-2.2) — composed from this service's own index plus one synchronous overlay call, never requiring the caller to hit multiple services itself.
- **Index maintenance** — consuming `operator-service`'s trip lifecycle events and `review-service`'s review events to keep its own read model current (`events-consumed.md`).
- **Health, metrics, OpenAPI exposure** — non-negotiable per `.claude/ARCHITECTURE_RULES.md`, same as every other service.

## Non-Responsibilities

- **Trips, fares, schedules, or fleets as source of truth.** All owned by `operator-service`. `search-service` never accepts a write that changes any of this data — even an "update my own copy" write is triggered only by consuming that service's events, never by a direct client call.
- **Live seat-level inventory as source of truth.** Owned by `inventory-service`. `search-service` overlays a cached view of seat *counts* for ranking/display, but never claims to be the authority a hold attempt should trust — see `boundaries.md` and `docs/architecture/seat-locking-flow.md`.
- **Seat map / seat-level selection UI.** Per `docs/architecture/api-inventory.md`, `inventory-service`'s "Trip Availability Query" is consumed directly by `customer-web`'s trip-detail view as well as by `search-service` — the seat-by-seat layout is not something `search-service` proxies or duplicates.
- **Review content.** Owned by `review-service`. `search-service` holds only a denormalized aggregate rating, never individual review text — see `domain-model.md`.
- **Booking, holds, or payment.** `search-service` is read-only, full stop. A search result feeds a traveler's *next* action (holding a seat via `inventory-service`), but `search-service` itself never initiates a hold, a booking, or a payment, and never calls `booking-service` or `payment-service` at all.
- **Cross-vertical search.** Phase 1 is bus-only. Becoming a bus+train+flight aggregator is an explicitly anticipated Phase 2+ evolution of this service's read model (`docs/architecture/high-level-design.md` §12) — not something this phase's design needs to generalize for today.

## Design Rationale for the Split

Restating and sharpening `docs/architecture/service-boundaries.md`'s reasoning specifically for this service: search and inventory-write are the same subject matter (a trip's seats) but fundamentally different access patterns — search is read-heavy, latency-sensitive, and tolerant of a small staleness window; inventory holds are write-heavy, contention-heavy, and require zero staleness tolerance (`docs/architecture/seat-locking-flow.md`). Forcing one service to be tuned for both would compromise both. Splitting them lets `search-service` be denormalized, cached, and horizontally scaled purely for query throughput, while `inventory-service` stays focused on correctness under contention.

The core trade-off this service is built around: **the search index lags the source of truth by however long event propagation takes.** This is accepted, not tolerated as a flaw, because nothing downstream of a search result treats it as authoritative — `inventory-service` re-validates availability at hold time regardless of what a search result showed (`service-boundaries.md`). A stale search result costs a traveler a retry; it never costs the platform a double-booked seat.
