# Payment Service — Events Consumed

Per `docs/architecture/event-catalog.md`'s delivery model: at-least-once, ordered only within a
partition keyed by the relevant entity id. Every handler below must be idempotent and must not
assume cross-entity ordering.

`payment-service` consumes **exactly one** Kafka event, and it consumes it for **reconciliation
only** — not as a trigger for money movement. The brief asks about consuming `BookingCreated` (*"if
required"*) and `BookingCancelled`; the analysis below shows `BookingCreated` is **not** required and
`BookingCancelled` is consumed only as a safety-net.

## From `booking-service`

| Event | Purpose for `payment-service` | Handling Notes |
|---|---|---|
| `BookingCancelled` | **Reconciliation safety-net only** — detect a refund-eligible cancellation whose captured payment has no corresponding `Refund`, and raise a discrepancy for support | See "The Refund-Trigger Reconciliation" below. `docs/architecture/event-catalog.md` lists `payment-service` as a `BookingCancelled` consumer *"only when a refund is owed"*; this consumption is exactly that, resolved to reconciliation rather than blind refund initiation |

### The Refund-Trigger Reconciliation (Why This Is Not a Refund Initiator)

This is the resolution to the discovered conflict documented in full in `boundaries.md`'s
"Relationship to `booking-service` — the Refund Trigger, Reconciled." In brief:

- The **authoritative** refund trigger is the **synchronous `Initiate Refund` API call** from
  `booking-service` (`use-cases.md`, `api-summary.md`) — the only channel that carries the
  `booking-service`-computed refund **amount**. `payment-service` cannot compute a policy-based
  amount from `BookingCancelled` alone, because it does not own cancellation policy
  (`docs/architecture/service-boundaries.md`).
- Consuming `BookingCancelled` therefore does **not** initiate a refund. It **verifies** that every
  refund-eligible cancellation (identifiable by `cancellationReason` plus a captured `Payment` for
  that `bookingReference`) already has a `Refund` initiated via the API. If, after a grace window,
  one is missing, `payment-service` records a `ReconciliationRecord` for support — never a blind
  automatic refund.
- Because both channels key on `(bookingReference / paymentReference)` and the refund idempotency
  key, this consumer can **never** create a second refund. If a `Refund` already exists, it is a
  no-op.

This keeps `docs/architecture/event-catalog.md`'s frozen listing literally true (`payment-service`
*does* consume `BookingCancelled` "when a refund is owed") while eliminating the double-refund hazard
that consuming it as an initiator would create. The recommended smallest change — a one-clause
clarification in `event-catalog.md` that this consumption is reconciliation-only — is flagged in
`boundaries.md`, not applied here.

### Why `BookingCreated` Is Deliberately Not Consumed

The brief asks whether `BookingCreated` should be consumed *"if required."* **It is not required, so
it is not consumed.** `docs/architecture/event-catalog.md` lists `BookingCreated`'s only consumer as
`analytics-service` (*"A booking entered `PENDING_PAYMENT` (funnel tracking)"*) — `payment-service`
is **not** a consumer, and adding it as one would be inventing a new consumer relationship the frozen
catalog does not contain. More fundamentally, `payment-service` has no need for it: a payment is
created by the **client calling `Initiate Payment` directly** (`docs/architecture/booking-flow.md`
step 3, `boundaries.md`'s Channel 1), carrying the `bookingReference` and amount in the request.
`payment-service` learns which booking to charge from that synchronous call, not from an event —
consuming `BookingCreated` would be a redundant, race-prone second source for a fact it already
receives authoritatively and synchronously. This is the same reasoning `booking-service` uses to
justify a **synchronous** reservation validation over an event (`docs/architecture/booking-flow.md`
step 2: *"an event-driven check would create a race where the client could reach the service before
it has consumed the corresponding event"*).

## What `payment-service` Deliberately Does Not Consume

- **`BookingCreated`** — not a consumer per the frozen catalog; the `bookingReference` arrives
  synchronously on `Initiate Payment` (above).
- **`BookingConfirmed`** — `notification-service`/`analytics-service`'s concern. A confirmed booking
  requires no payment action; the payment was already captured before `booking-service` confirmed.
- **`TripCancelled`** — `booking-service` consumes this and cascades cancellations, then requests
  refunds via `Initiate Refund` (`docs/services/booking-service/use-cases.md`'s "Handle Trip
  Cancelled"). `payment-service` refunding directly off `TripCancelled` would (a) duplicate the
  synchronous refund path and (b) require it to know which payments belong to the trip — a fact it
  does not own (it holds opaque `bookingReference`s, not trip ids). Refunds flow through
  `booking-service`, which owns the "which bookings, what amount" decision.
- **Any seat event** (`SeatBlocked`, `SeatReleased`) — `provider-integration-service`'s; irrelevant
  to a payment, which has no concept of a seat.
- **Any catalog event** (`TripPublished`, `TripUpdated`, `FareSnapshotUpdated`, ...) —
  `inventory-service`'s. A payment's amount is frozen at initiation
  (`data-ownership.md`); a later fare change never affects an existing payment.
- **`ReviewSubmitted`** — `search-service`/`analytics-service`'s; no payment relevance.
- **Its own payment/refund events** — a producer never consumes its own topic.

## Failure Considerations

- **`BookingCancelled` lag or loss** delays only the *reconciliation* check, not any refund — the
  refund itself is driven by the synchronous `Initiate Refund` call, which has its own retry and
  idempotency (`boundaries.md`). A lagging reconciliation consumer means a genuinely-missing refund
  (the rare case where the synchronous call was lost entirely) is caught later rather than sooner —
  an operational gap, not a correctness one, and exactly the kind of net that exists to catch the
  rare lost-call case in the first place.
- **Idempotency** — a redelivered `BookingCancelled` must re-run the same verification and reach the
  same conclusion (a `Refund` exists → no-op; still missing → the discrepancy is already recorded,
  do not record a duplicate), keyed by `bookingReference`.

## Relationship to the Synchronous Inbound Calls (Not Events)

For completeness — `payment-service`'s most important inbound interactions are **not** events at all:
`Initiate Payment` (client→service, `api-gateway`) and `Initiate Refund`
(`booking-service`→service, service-to-service), plus `Handle Gateway Webhook` (gateway→service).
Those are covered in `use-cases.md` and `api-summary.md`. This document covers only the Kafka
consumption surface, which is deliberately minimal — a single reconciliation consumer — because
`payment-service`'s coordination-critical inputs are synchronous by design, for the same
race-avoidance reason the platform prefers synchronous calls on correctness-critical paths
(`docs/architecture/booking-flow.md` step 2).
