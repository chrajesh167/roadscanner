# Booking Service — Responsibilities

## Responsibilities

- **Booking lifecycle & state machine** — the exact state machine `docs/architecture/booking-flow.md`
  defines (`PENDING_PAYMENT → CONFIRMED → COMPLETED`, with `CANCELLED` reachable from either
  non-terminal state). `booking-service` owns this state machine exclusively — no other service
  mutates a booking's state, ever, under any circumstance.
- **Passenger details** — full name, age, gender, seat number per passenger, attached to a
  booking at creation time (FR-3.1).
- **Booking history** — every booking a traveler has ever made, queryable by that traveler,
  regardless of current status (FR-1.3). Nothing is ever deleted — see `data-ownership.md`.
- **Booking cancellation** — traveler-initiated (FR-3.5) and cascade-initiated (on `TripCancelled`,
  `docs/architecture/booking-flow.md` step 7), including the cancellation-reason bookkeeping that
  distinguishes *why* a booking ended up `CANCELLED` (see `domain-model.md`'s
  `CancellationReason`).
- **Booking validation** — a booking may only be created against a still-valid seat hold, for a
  trip `inventory-service` confirms is real and bookable, with passenger details that satisfy
  basic structural rules (non-empty name, plausible age, one passenger per held seat).
- **Booking ownership enforcement** — a booking is only ever visible to the traveler who created
  it, an operator viewing bookings against their own trip, or an admin/support agent — see
  `boundaries.md`'s "Booking ↔ Auth" section.
- **Booking audit metadata** — timestamps for every state transition (created, confirmed,
  cancelled, completed), the cancellation reason, and a support-visible flag for the one edge case
  where RoadScanner's own state and the provider's diverge (`docs/architecture/booking-flow.md`'s
  "provider confirmation fails after payment already succeeded" scenario).
- **Seat-selection composition** — the client-facing view that merges `inventory-service`'s static
  `SeatLayout` with `provider-integration-service`'s live per-seat status into one response, per
  `docs/services/inventory-service/sequence-diagrams.md` flow 4 (which explicitly assigns this
  composition to `booking-service`, not `inventory-service` or `customer-web`).
- **Seat-hold orchestration** — resolving a trip's `ProviderMapping` via `inventory-service`, then
  placing/releasing a hold with `provider-integration-service`, and tracking that hold locally
  until it either becomes a booking or expires (`domain-model.md`'s `SeatHold`).
- **Ticket retrieval** — once a booking is `CONFIRMED`, retrieving the provider-issued ticket via
  `provider-integration-service` exactly once, persisting it, and serving it back to the traveler
  on demand thereafter without re-asking the provider (FR-3.6).
- **Booking verification for `review-service`** — a narrow, internal query answering "does a
  verified, completed booking by this traveler for this trip exist," backing FR-7.2. See
  `api-summary.md`.
- **Health, metrics, OpenAPI exposure** — non-negotiable per `.claude/ARCHITECTURE_RULES.md` and
  NFR-15, same as every other service.

## Non-Responsibilities

- **Trip catalog, cities, stations, routes, operators (as data), buses, static seat layouts,
  provider mappings, fare authority, catalog synchronization.** Entirely `inventory-service`'s.
  `booking-service` reads a `Trip`'s shape and its `ProviderMapping` at hold time; it never stores
  a copy of the catalog itself, and never becomes a second source of truth for what a trip is —
  see `data-ownership.md`.
- **Live seat availability, seat locks, seat reservations as owned state.** Never computed or
  guaranteed here. Every hold, release, and confirmation is a call to
  `provider-integration-service`; `booking-service` tracks only the *reference* to a hold it
  requested (`SeatHold`), never the underlying locking mechanism.
- **Provider authentication, provider sessions, provider-specific retries, circuit breakers, rate
  limiting, or any provider-specific HTTP integration of any kind.** `booking-service` never
  imports a provider-specific type and never calls a provider's API directly — every provider
  interaction goes through `provider-integration-service`'s canonical, provider-agnostic port,
  the same rule every other calling service on this platform follows
  (`docs/services/provider-integration-service/boundaries.md`).
- **Payment processing, gateway integration, or the payment ledger.** `payment-service`'s,
  entirely. `booking-service` reacts to `PaymentCompleted`/`PaymentFailed`/`PaymentTimedOut` and
  requests refunds; it never talks to a payment gateway and never stores card or bank details
  (NFR-12).
- **The decision of whether a refund is owed, in the general case.** `booking-service` decides
  *when* to request a refund (a `CONFIRMED` booking is cancelled) but the cancellation-fee policy
  itself is `operator-service`'s configuration to own
  (`docs/architecture/service-boundaries.md`'s `payment-service` entry: *"the decision of whether
  a refund is owed... that's a booking-service/operator-service policy decision"*). See
  `boundaries.md`'s "Relationship to `operator-service`" for the current, flagged state of that
  dependency.
- **Notification delivery, or the decision of when to notify.** `notification-service` reacts to
  `BookingConfirmed`/`BookingCancelled` on its own; `booking-service` never calls it directly and
  never decides notification content or timing (`docs/architecture/service-boundaries.md`'s
  `notification-service` entry).
- **Search ranking, filtering, or trip discovery.** `search-service`'s, entirely — no relationship
  exists between the two services (`boundaries.md`).
- **Review or rating data.** `review-service`'s. `booking-service` only ever answers "does a
  qualifying booking exist," never stores or computes a rating itself.

## Design Rationale for the Split

`booking-service` exists because *coordinating an outcome across several owning services* is
itself a distinct responsibility with its own reason to change — a new payment method, a new
cancellation rule, or a new step in the booking funnel changes this service without requiring
`inventory-service` or `provider-integration-service` to change at all, and vice versa: a new bus
operator or a new provider changes those services without `booking-service` needing to know.
Folding orchestration into either owning service would make that service responsible for a second
job with a different change cadence and a different consistency requirement (`booking-service`'s
own path is the one place on this platform, per `docs/architecture/high-level-design.md` §6, that
needs saga-grade strong consistency — neither `inventory-service` nor `provider-integration-service`
needs that for their own concerns).

**Trade-off accepted:** every booking operation now costs at least two service hops
(`inventory-service` for catalog facts, `provider-integration-service` for live provider action)
before `booking-service` can act. Accepted for the same reason `docs/architecture/service-boundaries.md`
accepts it everywhere else on this platform: the alternative is `booking-service` re-implementing
catalog knowledge or provider integration itself, which would make it a second place either has to
be maintained.
