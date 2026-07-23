# Booking Service — Overview

> **Reviewed, 2026-07-23 — final pass before implementation.** Confirmed `booking-service` is the
> sole orchestrator of Seat Hold, Booking creation, and Provider Confirmation, with the client's
> direct call to `payment-service` documented as the one, deliberate, non-orchestration exception
> (`boundaries.md`'s "Single-Orchestrator Verification"). Confirmed and justified `Hold Seats`
> remaining a separate client-facing step, against FR-3.2's own wording
> (`boundaries.md`'s "Why `Hold Seats` Is a Separate Client-Facing Step"). Added a recommendation
> — not implemented, since it would mean redesigning `inventory-service`'s frozen contract — that
> `bookable` should eventually fold in `ProviderMapping` presence
> (`boundaries.md`'s "Recommendation... `inventory-service` Should Expose 'Bookable' as Its Own
> Signal"). Added `booking-state-machine.md` as the detailed, standalone state-transition
> reference. No state, transition, contract, or existing-service boundary changed by this pass —
> see "Implementation Readiness" below.

## Purpose

`booking-service` is the platform's **orchestration layer for the booking lifecycle** — the
service that turns "a traveler wants this seat" into a durable, correctly-consistent booking
record, by composing facts and actions from services that each own a narrower slice of the
problem. It is the **source of truth for booking records**: who booked, which passengers, which
seats, what status, what history — nothing else on the platform tracks this data.

It owns no catalog data, no live seat state, no payment processing, and no provider integration
of its own. Per `docs/architecture/service-boundaries.md`'s `booking-service` entry: *"the
orchestrator of an outcome, not the owner of the mechanics behind it."*

## Bounded Context

**In:** the booking lifecycle and state machine (`docs/architecture/booking-flow.md`), passenger
details attached to a booking, booking history, booking cancellation, booking validation, and the
client-facing composition of catalog shape (`inventory-service`) with live seat state
(`provider-integration-service`) into one seat-selection view.

**Out — never owned here:**

- **Trip catalog, cities, routes, stations, operators, provider mappings, seat layouts** —
  `inventory-service`.
- **Live seat availability, seat holds, seat reservations** — `provider-integration-service`.
- **Provider authentication, provider sessions, provider retries, circuit breakers** —
  `provider-integration-service`.
- **Payment transactions, refund execution** — `payment-service` (not yet built).
- **Notification delivery** — `notification-service` (not yet built).

## Where It Sits

- `docs/architecture/high-level-design.md` §3 (service inventory) and §6 (booking consistency —
  the one place this platform accepts saga/outbox overhead for strong consistency).
- `docs/architecture/service-boundaries.md`'s `booking-service` entry.
- `docs/architecture/booking-flow.md` — the step-by-step lifecycle this service implements
  directly; `docs/architecture/seat-locking-flow.md` and `docs/architecture/payment-flow.md` for
  the mechanics of the two services it composes.
- `docs/services/inventory-service/` and `docs/services/provider-integration-service/` — the two
  existing, frozen services this one calls. Nothing in either of those directories changes as a
  result of this one; see `boundaries.md`.

## Orchestration, Not Ownership

`booking-service` composes three kinds of fact into one outcome:

1. **Catalog facts** (`inventory-service`) — is this trip real, what does it cost, what does its
   seat layout look like, and which provider (if any) backs it.
2. **Live provider actions** (`provider-integration-service`) — is this specific seat actually
   available right now, can it be held, can that hold be turned into a confirmed reservation.
3. **Payment outcome** (`payment-service`, future) — did the traveler actually pay.

`booking-service` never re-implements any of the three — it calls the owning service for each,
and its own domain model exists to hold the *outcome* of that composition (a `Booking`) and drive
it through a state machine, not to duplicate any of the three services' own data as a system of
record. See `data-ownership.md` for exactly what is, and is not, a durable copy.

## Booking Flow (Summary)

```
Customer
  │
  ▼
Booking Service ──► Inventory Service (validate trip metadata + ProviderMapping)
  │
  ▼
Booking Service ──► Provider Integration Service (hold seat(s) with the provider)
  │
  ▼
Booking Service: create PENDING_PAYMENT booking
  │
  ▼
Payment Service (future) ──► PaymentCompleted
  │
  ▼
Booking Service ──► Provider Integration Service (confirm the provider reservation)
  │
  ▼
Booking Service: status = CONFIRMED, publish BookingConfirmed
```

See `sequence-diagrams.md` for the full, precise call sequence (which is one step more granular
than this summary — seat selection/hold and booking creation are two distinct client-facing
interactions, not one) and `use-cases.md`/`domain-model.md` for why.

## Cancellation Flow (Summary)

```
Customer
  │
  ▼
Booking Service
  │
  ▼
Provider Integration Service (release the hold, if still PENDING_PAYMENT)
  │
  ▼
Booking Service: status = CANCELLED, publish BookingCancelled
```

**This summary hides a real, flagged gap** — a `CONFIRMED` booking's cancellation cannot reverse
the provider-side confirmation today, because no such capability exists anywhere on the platform
yet. See `boundaries.md`'s "Known Gap" section for the full explanation and the interim policy
this documentation set adopts.

## Architectural Ambiguities Found and Resolved in This Pass

Building this specification required reading the frozen `inventory-service` and
`provider-integration-service` documentation closely, because `booking-service` is the first
consumer of several of their contracts. Four real gaps surfaced — each is resolved below with the
least-invasive choice consistent with **not redesigning either existing service**, and each is
flagged again, in more detail, at its point of relevance:

1. **No provider-side reversal for an already-confirmed booking.**
   `docs/services/provider-integration-service/boundaries.md`'s "Known Gap" section explicitly
   defers this decision to `booking-service`'s own design. Resolved here as: post-confirmation
   cancellation is **refund-only, no provider-side reversal**, today. See `boundaries.md`.
2. **A trip with no `ProviderMapping` cannot be booked.** `inventory-service`'s domain model
   states a first-party trip with no provider equivalent is "not an error state" at the catalog
   level — but it means `booking-service` has no path to hold or confirm a seat for such a trip,
   since every provider operation requires a `providerType` + `providerTripId`. Resolved here as
   an explicit validation rule, not a defect. See `use-cases.md`'s "Create Seat Hold."
3. **`provider-integration-service` has no read-only "get reservation status" operation.**
   `docs/architecture/booking-flow.md` step 2 describes booking creation as validating the
   reservation via "a read call" to `provider-integration-service` — but that service's actual,
   frozen port set (`AuthenticateProvider`/`RefreshSession`/`SearchTrips`/`GetSeatMap`/
   `BlockSeat`/`ReleaseSeat`/`ConfirmBooking`/`DownloadTicket`/`GetProviderCapabilities`/
   `CheckProviderHealth`) has no such operation. Resolved here as a **local expiry check**
   against the `expiresAt` timestamp `provider-integration-service` already returned at hold
   time — see `domain-model.md`'s `SeatHold` and `use-cases.md`'s "Create Booking."
4. **`SeatReleased` is consumed by design but not yet published.**
   `docs/services/provider-integration-service/events-published.md` states `SeatReleased` is
   specified but **not yet implemented** by that service. `booking-service`'s design still
   documents consuming it (per `docs/architecture/event-catalog.md`), with the gap flagged as a
   producer-side prerequisite, not a `booking-service` defect. See `events-consumed.md`.

None of these required changing `inventory-service` or `provider-integration-service`'s existing,
frozen contracts — every resolution stays entirely inside `booking-service`'s own design.

## Implementation Readiness

**Yes — this documentation set is implementation-ready**, with the following carried-forward,
explicitly non-blocking conditions:

- **Two integration points have no real producer/service to build against yet**
  (`payment-service`, `operator-service`) and one event has no real producer yet
  (`SeatReleased`). Every consumer/client contract for all three is fully specified
  (`events-consumed.md`, `boundaries.md`), so implementation can proceed against those contracts
  now, with the adapters wired to real endpoints the moment each dependency exists — the same
  "contract ready, no real producer yet" posture already proven out by
  `provider-integration-service`'s FlixBus integration and `inventory-service`'s `operator-service`
  consumption before either upstream fully existed.
- **Post-confirmation cancellation is refund-only, with no provider-side reversal**, by explicit,
  documented decision (`boundaries.md`'s "Known Gap: Post-Confirmation Cancellation"). This is a
  real product limitation, not an implementation blocker — it is fully specified behavior
  (transition to `CANCELLED`, request a refund, do not call `provider-integration-service`), just
  not the complete capability a future `CancelBooking` port on `provider-integration-service`
  would eventually provide.
- **Only `PROVIDER_SYNCED` trips are bookable today**, by construction of `inventory-service`'s
  existing `ProviderMapping` model, not a `booking-service` defect (`use-cases.md`'s "A Trip With
  No `ProviderMapping` Cannot Be Held"). Fully specified as a validation rule; implementation can
  proceed without waiting on a first-party live-booking mechanism that doesn't exist yet anywhere
  on this platform.
- **The `operator-service` cancellation-policy dependency has an explicitly open interim-behavior
  question** (`boundaries.md`'s "Relationship to `operator-service`") — what `booking-service`
  does when that call has nowhere to go. This is the one place implementation cannot proceed on
  full autopilot; whoever implements `Cancel Booking` must pick an interim default (documented
  inline as a code comment/ADR at that point) and revisit it once `operator-service` exists. Every
  other use case in this specification has no such open question.

Nothing else in this specification is blocked. Domain model, state machine, every REST and Kafka
contract, every service relationship, and every failure mode are fully specified against the
frozen `inventory-service` and `provider-integration-service` contracts as they exist today.

## Documents in This Directory

| Document | Covers |
|---|---|
| `responsibilities.md` | Explicit responsibilities, non-responsibilities |
| `boundaries.md` | Every service relationship, the four flagged gaps in full, and their resolutions |
| `domain-model.md` | `Booking`, `SeatHold`, `Passenger`, `Ticket`, the state machine, invariants |
| `booking-state-machine.md` | The full state machine in detail — every transition, trigger, and Mermaid diagram |
| `use-cases.md` | Every inbound-port use case, client-facing and internal |
| `sequence-diagrams.md` | The full booking and cancellation flows, precisely, including the two-step hold/book split |
| `data-ownership.md` | What's authoritative here vs. a captured-at-a-point-in-time copy |
| `events-published.md` | `BookingCreated`, `BookingConfirmed`, `BookingCancelled` |
| `events-consumed.md` | `TripCancelled`, `SeatReleased`, and the not-yet-real payment events |
| `api-summary.md` | Client-facing and internal REST surface |
