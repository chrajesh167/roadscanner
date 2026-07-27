# Payment Service — Events Published

Topic: `payment-events` (single topic, discriminated by `eventType`, keyed by `paymentId` for
per-payment ordering) — matching the single-topic-per-domain convention `booking-service`'s
`booking-events`, `inventory-service`'s `CatalogTripEventMessage`, and
`provider-integration-service`'s `ProviderAuditMessage` already establish, applied here for
consistency, not independently reinvented.

## The Frozen Event Set (and How It Relates to the Brief's Requested Set)

`docs/architecture/event-catalog.md`'s "Payment Events" table is the frozen contract. It lists six
events; the brief's requested list overlaps but is not identical. Per `overview.md`'s Conflicts 1–3,
this document publishes the **frozen** names, adds back the frozen events the brief omitted, and
flags the one brief-requested event that is not yet in the frozen catalog:

| Event (this service publishes) | Consumers (frozen) | Purpose | Relationship to the brief's list |
|---|---|---|---|
| `PaymentCreated` | `analytics-service` | A payment intent was created for a booking — funnel tracking, the payment analogue of `BookingCreated` | **Brief-requested; not yet in the frozen catalog** — Conflict 2. Analytics-only, no correctness weight; recommended as a one-row addition to `event-catalog.md` |
| `PaymentCompleted` | `booking-service`, `analytics-service` | The gateway confirmed success (money captured) — `booking-service` confirms the booking | **The brief calls this `PaymentSucceeded`** — Conflict 1. Published under the frozen name `PaymentCompleted`, which `booking-service` already consumes |
| `PaymentFailed` | `booking-service`, `analytics-service` | The gateway declined or errored — `booking-service` cancels the booking, releases the seat | Matches the brief and the frozen catalog exactly |
| `PaymentTimedOut` | `booking-service`, `analytics-service` | No gateway outcome arrived within the acceptable window | **Frozen; omitted by the brief** — Conflict 3. Added back because `event-catalog.md` requires it and `booking-service` consumes it |
| `RefundInitiated` | `analytics-service` | A refund has been requested from the gateway | Matches the brief and the frozen catalog |
| `RefundCompleted` | `notification-service`, `analytics-service` | The gateway confirmed the refund — traveler notified (FR-6.2) | Matches the brief and the frozen catalog |
| `RefundFailed` | `notification-service`, `analytics-service` | The gateway refund attempt failed — routed to support for manual handling | **Frozen; omitted by the brief** — added back; `docs/architecture/payment-flow.md`'s "a failed refund routes to support" |

**Consumer lists are `docs/architecture/event-catalog.md`'s, unchanged.** This service does not
invent a consumer for any event; it produces into the frozen contract exactly as written.

## `PaymentCreated` — Informational Event (Frozen)

`PaymentCreated` is **kept**, by explicit freeze decision (`overview.md`), and its role is frozen as
**informational only**:

- **Purpose** — analytics, observability, reporting, and future downstream consumers (e.g. a future
  `analytics-service` payment funnel, or a future `fraud-service` scoring pipeline). It is the
  payment-side analogue of `BookingCreated`.
- **No business-correctness responsibility.** It is emitted the instant a `Payment` intent is
  persisted (`CREATED`), before any gateway outcome exists. No booking, payment, or refund decision
  anywhere on the platform reads it or branches on it.
- **`booking-service` must never depend on it.** The booking flow is driven **exclusively** by the
  frozen business events — `PaymentCompleted`, `PaymentFailed`, `PaymentTimedOut`, `RefundCompleted`
  (and `RefundInitiated`/`RefundFailed`). If `PaymentCreated` were lost, delayed, or never consumed,
  every booking outcome would still be correct — only a dashboard would miss a data point.
- **Delivery expectations match its role.** Like `BookingCreated`, it is best-effort-useful, not
  correctness-critical: at-least-once delivery still applies, but no consumer-lag alarm on this event
  is paging-worthy (unlike the business events below).

This distinction — one informational event vs. the correctness-critical business events — is the
clean line the freeze draws: **`PaymentCreated` reports that a payment *began*; the business events
report what *happened to the money*.** Only the latter drive any state anywhere.

## Event ↔ Internal State Mapping

Each event is emitted on a specific `Payment`/`Refund` transition (`payment-state-machine.md`):

| Internal transition | Event emitted |
|---|---|
| `Payment` created (`CREATED`) | `PaymentCreated` |
| `→ AUTHORIZED` | *(none — internal waypoint)* |
| `→ CAPTURED` | `PaymentCompleted` |
| `→ FAILED`, or `→ CANCELLED` (voided before capture) | `PaymentFailed` |
| `→ EXPIRED` | `PaymentTimedOut` |
| `Refund → REQUESTED` (`Payment → REFUND_PENDING` for a full refund) | `RefundInitiated` |
| `Refund → COMPLETED` (`Payment → REFUNDED` for a full refund) | `RefundCompleted` |
| `Refund → FAILED` | `RefundFailed` |

`AUTHORIZED` deliberately emits **no** external event — it is an internal auth-then-capture waypoint,
invisible to the rest of the platform, so `booking-service`'s frozen state machine sees only the
outcomes it already knows how to consume. `CANCELLED` surfaces as `PaymentFailed` so a payment voided
before capture still causes `booking-service` to cancel the booking and release the seat
(`domain-model.md`'s `PaymentStatus` mapping).

## Payload Shape (Conceptual — Not an OpenAPI/Avro Schema Yet)

Every event on this topic carries at minimum: `eventType`, `paymentId`, `bookingReference`,
`travelerId`, `amount` (`Money`), `status`, `occurredAt`, plus the correlation/trace id
(NFR-16). Refund events additionally carry `refundId` and `refundAmount`; `PaymentFailed`/
`PaymentTimedOut` carry a coarse, gateway-agnostic failure classification (never a raw
gateway/instrument detail — NFR-12). Exact wire schema is this service's own implementation decision,
matching `docs/architecture/event-catalog.md`'s stated scope (*"exact payload schemas are not defined
in this document"*) and `booking-service`'s identical hedge.

## Why the Money Movement Is Recorded Before the Event

A payment/refund event is always a notification of an **already-settled fact** — the state
transition and the ledger line are written first, then the event is published (intended atomically,
via the outbox below). No consumer of `PaymentCompleted` ever observes it before the capture was
actually recorded, and no consumer of `RefundCompleted` before the refund was actually recorded.
This mirrors `booking-service`'s "Why the Provider-Side Action Happens Before the Event, Not After"
— the platform's consistent posture that events report settled facts, never trigger the settling.

## Failure Considerations

Same general model as every event on this platform (`docs/architecture/event-catalog.md`):
at-least-once delivery, ordering guaranteed only within the `paymentId` partition. A consumer that
sees `PaymentCompleted` (or any event here) twice for the same payment must treat the repeat as a
no-op.

- **`PaymentCreated` carries no correctness weight** — analytics funnel only. A lost or delayed
  delivery affects a dashboard, never a payment's state (identical to `BookingCreated`).
- **`PaymentCompleted` / `PaymentFailed` / `PaymentTimedOut` are the correctness-critical rows** —
  `booking-service` drives the booking state machine off them. `docs/architecture/event-catalog.md`
  flags exactly the case that makes them delicate: a `PaymentCompleted` arriving **after** a
  `PaymentTimedOut`-driven cancellation (the late-success edge case). This service emits it
  truthfully anyway (`sequence-diagrams.md`'s flow 5); `booking-service` turns it into an automatic
  refund plus a flag. Consumer lag here is paging-worthy — it directly delays booking confirmation.
- **`RefundCompleted` / `RefundFailed`** feed `notification-service` and, for `RefundFailed`, the
  support path. A stuck consumer here delays a traveler's refund confirmation — customer-trust
  sensitive, the same reasoning `booking-service` applies to its own `BookingCancelled` row.

## Outbox Pattern (Per `docs/architecture/high-level-design.md` §6)

`payment-service`'s Postgres write (the state transition + ledger line) and its corresponding Kafka
publish are intended to commit atomically via a **transactional outbox** — the one path on this
platform (booking↔payment) that adopts this pattern, so there is never a window where this service's
own state and its published event disagree (NFR-10). Per `docs/architecture/payment-flow.md`'s own
timing (*"that formalization happens when booking-service and payment-service are actually
implemented"*), this is the target design; the exact outbox mechanics are an implementation
decision, the same hedge `booking-service`'s `events-published.md` applies. The outbox specifically
protects the boundary between this service's write and its published event, and the booking↔payment
saga between the two services.

## What's Deliberately Not Published Here

- **Any booking event** (`BookingCreated`, `BookingConfirmed`, `BookingCancelled`) —
  `booking-service`'s, unaffected by this specification. `payment-service` reacts to money outcomes;
  it never asserts anything about a booking's state.
- **Any seat or catalog event** — `provider-integration-service`'s and `inventory-service`'s.
- **A per-attempt or per-gateway-call telemetry event** — not required by any functional
  requirement; if `analytics-service` needs attempt-level funnel data beyond `PaymentCreated`/
  `PaymentFailed`, that is a future addition to this document, not assumed here (same posture
  `booking-service` takes toward a hypothetical "booking viewed" event).
- **A raw gateway webhook re-publish** — webhooks are consumed and translated into the canonical
  events above; the raw gateway payload is never re-emitted onto Kafka (it may contain
  instrument-adjacent data — NFR-12).
