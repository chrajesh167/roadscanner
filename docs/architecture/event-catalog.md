# Event Catalog

This is the canonical, detailed event catalog for Phase 1, expanding `high-level-design.md` §5 with the purpose and failure handling needed before implementation begins. Event names here are stable identifiers for design purposes — exact payload schemas are not defined in this document (see `docs/api/` once a service is implemented).

> **Corrected by architecture review, 2026-07-22.** Seat-level events moved from
> `inventory-service` to `provider-integration-service` and were renamed (`SeatHeld` →
> `SeatBlocked`); `inventory-service` gained its own "Catalog Events" (below), which
> `search-service` now consumes instead of `operator-service`'s directly. See
> `docs/services/inventory-service/` for the full reasoning.

## Delivery Model & General Failure Handling

All events are delivered via Kafka with **at-least-once** semantics. Two consequences apply to every event below, so they're stated once here instead of repeated per row:

- **Consumers must be idempotent.** A consumer may see the same event more than once and must treat a repeat as a no-op (e.g., keyed by booking ID / trip ID / event ID), not re-apply the effect.
- **Ordering is only guaranteed within a partition.** Events are partitioned by the relevant entity ID (trip ID, booking ID) so that events about the *same* entity arrive in order; there is no cross-entity ordering guarantee, and consumers must not assume one.

A consumer that is down or lagging does not lose events — Kafka retains them and the consumer catches up from its last committed offset. The failure modes documented per event below are the ones that are specific to that event's business meaning, not generic infrastructure failure.

## Trip Management Events (First-Party, `operator-service`)

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `TripPublished` | `operator-service` | `inventory-service` | A new first-party trip/schedule is available for booking |
| `TripUpdated` | `operator-service` | `inventory-service` | Fare, schedule, or capacity change on an existing first-party trip |
| `TripCancelled` | `operator-service` | `inventory-service` | Operator cancels an entire trip |
| `RouteUpdated` | `operator-service` | `inventory-service` | A route's shape (origin/destination/distance) changed |
| `OperatorUpdated` | `operator-service` | `inventory-service` | An operator's display/profile fields changed |

**Corrected by architecture review:** `search-service` and `booking-service` no longer consume
this topic directly — `operator-service` only ever knows about first-party trips, and both
services need the *merged* catalog (first-party plus provider-sourced). They consume
`inventory-service`'s own re-published events instead — see "Catalog Events" below.

**Failure considerations:** if `inventory-service` is behind on `TripPublished`, the trip is
simply not yet in the catalog — no data is lost, it becomes available as soon as the consumer
catches up, and nothing downstream is affected until `inventory-service` itself re-publishes.

## Catalog Events (Merged, `inventory-service`)

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `TripPublished` | `inventory-service` | `search-service` | A trip (first-party or provider-synced) enters the merged catalog |
| `TripUpdated` | `inventory-service` | `search-service` | Fare, schedule, or route changed on a catalog trip |
| `TripCancelled` | `inventory-service` | `search-service`, `booking-service`, `notification-service`, `analytics-service` | A catalog trip is no longer bookable — same downstream cascade the old `operator-service`-sourced version triggered, now sourced from the merged catalog so it also covers provider-sourced trips |
| `RouteUpdated` | `inventory-service` | `search-service` | Re-published after ingesting `operator-service`'s `RouteUpdated` |
| `OperatorUpdated` | `inventory-service` | `search-service` | Re-published after ingesting `operator-service`'s `OperatorUpdated` |
| `FareSnapshotUpdated` | `inventory-service` | `search-service` | A fare changed independent of a broader trip update (e.g., discovered by provider catalog sync) |
| `CatalogSyncCompleted` / `CatalogSyncFailed` | `inventory-service` | `analytics-service` | Observability into provider catalog synchronization health |

**Same event names, different topic, satisfies "one publisher per event" per-topic** — see
`docs/services/inventory-service/events-published.md` for why this is deliberate, not a naming
collision. `TripCancelled` remains the highest-stakes row: `booking-service` must process it to
cascade cancellations/refunds to every affected `CONFIRMED` booking (see `booking-flow.md`) — a
stuck consumer here directly delays refunds, so this topic's consumer lag is paging-worthy.

## Provider Integration Events (`provider-integration-service`)

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `SeatBlocked` | `provider-integration-service` | `analytics-service` | A seat reservation was created with a provider (renamed from `SeatHeld` — corrected by architecture review; previously misattributed to `inventory-service`) |
| `SeatReleased` | `provider-integration-service` | `booking-service`, `analytics-service` | A seat reservation ended without becoming a confirmed booking (TTL expiry, explicit release, or a cascaded `TripCancelled`) |
| `ProviderUnavailable` | `provider-integration-service` | none yet | A provider's health state transitioned into `UNAVAILABLE` |
| `ProviderRecovered` | `provider-integration-service` | none yet | A provider's health state transitioned from `UNAVAILABLE` back to `HEALTHY` |
| `SessionExpired` | `provider-integration-service` | none yet | A provider session was swept as expired without being refreshed |

**Design note:** seat reservation *creation* is a synchronous API call (see `seat-locking-flow.md`)
because the client is waiting on it and NFR-2 requires sub-second response — `SeatBlocked` is
fired afterward purely for analytics/observability, never on the coordination-critical path.
`booking-service` does not wait for or depend on `SeatBlocked`; it validates a reservation
synchronously against `provider-integration-service` instead (see `booking-flow.md`), specifically
to avoid a race where a booking references a reservation the event pipeline hasn't delivered yet.

**Failure considerations:** `SeatReleased` can legitimately fire twice for the same seat (e.g.,
TTL expiry and an explicit release racing each other) — consumers must treat a second release for
an already-released seat as a no-op, not an error. `ProviderUnavailable`/`ProviderRecovered`/
`SessionExpired` have no consumers yet because `booking-service`, `search-service`, and
`inventory-service` — the services expected to eventually consume the first two to route around a
degraded provider without polling its health endpoint — don't exist yet either (or, for
`inventory-service`, don't currently plan to consume them — see
`docs/services/inventory-service/events-consumed.md`). `analytics-service` would consume all three
for reporting.

## Booking Lifecycle Events

| Event | Producer | Consumers | Purpose |
|---|---|---|---|
| `BookingCreated` | `booking-service` | `analytics-service` | A booking entered `PENDING_PAYMENT` (funnel tracking) |
| `BookingConfirmed` | `booking-service` | `notification-service`, `analytics-service` | Payment succeeded **and** the provider confirmed the reservation; booking is now confirmed |
| `BookingCancelled` | `booking-service` | `payment-service`, `notification-service`, `analytics-service` | Booking did not/no longer results in a confirmed reservation (payment failure/timeout, provider confirmation failure, traveler cancellation, or trip cancellation) |

**Corrected by architecture review:** neither event is consumed by `inventory-service` (it was
never really an ingredient of a catalog decision) nor, as an *asynchronous trigger*, by
`provider-integration-service`. The provider-side confirm/release call now happens **synchronously**,
inside `booking-service`'s own `PaymentCompleted`/`PaymentFailed` handling, *before* either event is
emitted — see `booking-flow.md` steps 4–5. Both events are now purely downstream notifications of
an already-settled fact, not triggers for further seat-state mutation.

**Failure considerations:** `payment-service` consumes `BookingCancelled` only when a refund is
owed (i.e., the booking had reached `CONFIRMED` before being cancelled, or a provider-side
confirmation failure requires an automatic refund per `booking-flow.md`'s new edge case) — a
cancellation from the `PENDING_PAYMENT` state (payment never succeeded) requires no refund action.

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
