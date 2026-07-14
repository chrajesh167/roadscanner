# Event Catalog

This is the canonical, detailed event catalog for Phase 1, expanding `high-level-design.md` §5 with the purpose and failure handling needed before implementation begins. Event names here are stable identifiers for design purposes — exact payload schemas are not defined in this document (see `docs/api/` once a service is implemented).

## Delivery Model & General Failure Handling

All events are delivered via Kafka with **at-least-once** semantics. Two consequences apply to every event below, so they're stated once here instead of repeated per row:

- **Consumers must be idempotent.** A consumer may see the same event more than once and must treat a repeat as a no-op (e.g., keyed by booking ID / trip ID / event ID), not re-apply the effect.
- **Ordering is only guaranteed within a partition.** Events are partitioned by the relevant entity ID (trip ID, booking ID) so that events about the *same* entity arrive in order; there is no cross-entity ordering guarantee, and consumers must not assume one.

A consumer that is down or lagging does not lose events — Kafka retains them and the consumer catches up from its last committed offset. The failure modes documented per event below are the ones that are specific to that event's business meaning, not generic infrastructure failure.

## Trip Management Events

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `TripPublished` | `operator-service` | `inventory-service`, `search-service` | A new trip/schedule is available for booking |
| `TripUpdated` | `operator-service` | `inventory-service`, `search-service` | Fare, schedule, or capacity change on an existing trip |
| `TripCancelled` | `operator-service` | `inventory-service`, `booking-service`, `notification-service`, `analytics-service` | Operator cancels an entire trip |

**Failure considerations:** if `inventory-service` is behind on `TripPublished`, the trip is simply not yet bookable — no data is lost, it becomes bookable as soon as the consumer catches up. `TripCancelled` is the higher-stakes one: `booking-service` must process it to cascade cancellations/refunds to every affected `CONFIRMED` booking (see `booking-flow.md`) — a stuck consumer here directly delays refunds, so this topic's consumer lag is a paging-worthy metric, not just an informational one.

## Seat Inventory Events

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `SeatHeld` | `inventory-service` | `analytics-service` | A seat hold was created |
| `SeatReleased` | `inventory-service` | `booking-service`, `analytics-service` | A seat hold ended without becoming a booking (TTL expiry, explicit cancellation, or a cascaded `TripCancelled`) |

**Design note:** seat hold *creation* is a synchronous API call (see `seat-locking-flow.md`) because the client is waiting on it and NFR-2 requires sub-second response — `SeatHeld` is fired afterward purely for analytics/observability, it is never on the coordination-critical path. `booking-service` does not wait for or depend on `SeatHeld` to do its job; it validates a hold synchronously against `inventory-service` instead (see `booking-flow.md`), specifically to avoid a race where a booking references a hold the event pipeline hasn't delivered yet.

**Failure considerations:** `SeatReleased` can legitimately fire twice for the same seat (e.g., TTL expiry and an explicit cancellation racing each other) — consumers must treat a second release for an already-released seat as a no-op, not an error.

## Booking Lifecycle Events

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `BookingCreated` | `booking-service` | `analytics-service` | A booking entered `PENDING_PAYMENT` (funnel tracking) |
| `BookingConfirmed` | `booking-service` | `inventory-service`, `notification-service`, `analytics-service` | Payment succeeded; booking is now confirmed |
| `BookingCancelled` | `booking-service` | `inventory-service`, `payment-service`, `notification-service`, `analytics-service` | Booking did not/no longer results in a confirmed reservation (payment failure/timeout, traveler cancellation, or trip cancellation) |

**Failure considerations:** `inventory-service` consumes `BookingConfirmed` to convert a still-active hold into a permanent allocation. This is safe as an asynchronous step specifically because the hold's TTL is set comfortably longer than the maximum acceptable payment-processing time (see `booking-flow.md`) — by the time `BookingConfirmed` is possible at all, the hold is guaranteed still active. `payment-service` consumes `BookingCancelled` only when a refund is owed (i.e., the booking had reached `CONFIRMED` before being cancelled) — a cancellation from the `PENDING_PAYMENT` state (payment never succeeded) requires no refund action.

## Payment Events

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `PaymentCompleted` | `payment-service` | `booking-service`, `analytics-service` | Payment gateway confirmed success |
| `PaymentFailed` | `payment-service` | `booking-service`, `analytics-service` | Payment gateway declined or errored |
| `PaymentTimedOut` | `payment-service` | `booking-service`, `analytics-service` | No gateway confirmation arrived within the acceptable window |
| `RefundInitiated` | `payment-service` | `analytics-service` | A refund has been requested from the gateway |
| `RefundCompleted` | `payment-service` | `notification-service`, `analytics-service` | Gateway confirmed the refund |
| `RefundFailed` | `payment-service` | `notification-service`, `analytics-service` | Gateway refund attempt failed — routed to support for manual handling |

**Failure considerations (the important one):** a `PaymentCompleted` event can, in rare cases, arrive **after** `booking-service` has already cancelled the booking due to `PaymentTimedOut` (a late gateway confirmation). `payment-service` still records the payment as completed for financial accuracy, but `booking-service` cannot silently re-confirm a booking whose seat may already be resold. This must trigger an automatic refund plus a support-visible flag, never a silent "keep the money" outcome. See `payment-flow.md` for the full scenario.

## Review Events

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `ReviewSubmitted` | `review-service` | `search-service`, `analytics-service` | A traveler reviewed a completed trip |

**Failure considerations:** none beyond the general model — reviews are not on any latency- or consistency-critical path.

## Explicitly Not Covered Here

Authentication has no events — see `authentication-flow.md`; login/token issuance is synchronous request/response, not part of the event-driven surface.
