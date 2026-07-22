# Booking Service — Events Consumed

Per `docs/architecture/event-catalog.md`'s delivery model: at-least-once, ordered only within a
partition keyed by the relevant entity id. Every handler below must be idempotent and must not
assume cross-entity ordering.

## From `inventory-service`

| Event | Purpose for `booking-service` | Handling Notes |
|---|---|---|
| `TripCancelled` | Cascade-cancel every `CONFIRMED` and `PENDING_PAYMENT` booking against the cancelled trip, with a full refund for `CONFIRMED` bookings regardless of normal policy | `docs/architecture/booking-flow.md` step 7. This is the one row in this document worth treating as paging-worthy consumer lag, same reasoning `docs/architecture/event-catalog.md` applies to `inventory-service`'s own consumption of it — see `events-published.md` |

`booking-service` deliberately does **not** consume `TripPublished`, `TripUpdated`,
`RouteUpdated`, `OperatorUpdated`, or `FareSnapshotUpdated` — none of those change anything about
an *existing* booking, and `booking-service` re-reads current trip facts synchronously, on demand,
whenever it needs them (`use-cases.md`'s "Get Seat Selection View" / "Hold Seats"), rather than
maintaining a derived copy of the catalog.

## From `provider-integration-service`

| Event | Purpose for `booking-service` | Handling Notes |
|---|---|---|
| `SeatReleased` | Detect a hold that expired (or was released) without a corresponding booking action ever completing — transition/discard the affected `SeatHold` or `PENDING_PAYMENT` booking | **Specified, but not yet published by `provider-integration-service`'s current implementation** — see `docs/services/provider-integration-service/events-published.md`: *"`SeatBlocked`/`SeatReleased`... are not yet published by this service's current implementation... flagged here as required before `booking-service` can rely on them."` `booking-service`'s design still documents consuming it, per `docs/architecture/event-catalog.md`'s stated consumer list, so the contract is ready the moment the producer catches up — the same "contract ready, no real producer yet" posture already used elsewhere on this platform (see `overview.md`'s ambiguity #4). Until then, `use-cases.md`'s "Sweep Stale Holds" is the interim safety net |

`booking-service` deliberately does **not** consume `SeatBlocked` (analytics-only, no correctness
role for `booking-service` — it already knows about its own holds via `SeatHold`),
`ProviderUnavailable`/`ProviderRecovered`/`SessionExpired` (no consumers exist anywhere on the
platform yet per that service's own `events-published.md`; if `booking-service` eventually wants
to route around a degraded provider without waiting for a synchronous call to fail, that's a
future addition to this document, not assumed here).

## From `payment-service` (Not Yet Built)

| Event | Purpose for `booking-service` | Handling Notes |
|---|---|---|
| `PaymentCompleted` | Trigger provider confirmation and transition to `CONFIRMED` | `docs/architecture/booking-flow.md` step 4. Must handle a late delivery arriving after the booking was already cancelled by a timeout — see `use-cases.md`'s "Handle Payment Completed" |
| `PaymentFailed` | Transition to `CANCELLED` (`PAYMENT_FAILED`), release the hold | `docs/architecture/booking-flow.md` step 5 |
| `PaymentTimedOut` | Same as `PaymentFailed`, reason `PAYMENT_TIMED_OUT` | Distinct reason preserved for reconciliation, per `docs/architecture/payment-flow.md` |

**These three events have no real producer today** — `payment-service` does not exist. This
document specifies the contract `booking-service` is built against, the same "designed for, not
yet real" posture `boundaries.md`'s "Relationship to `payment-service`" describes. No consumption
logic can be exercised end-to-end until `payment-service` ships its own producer.

## Failure Considerations

- **`TripCancelled` lag** delays cancellation/refund cascades directly — treat consumer lag on
  this topic as paging-worthy, matching `inventory-service`'s own stated posture toward it.
- **`SeatReleased` lag or absence** (today, total absence) means an abandoned `PENDING_PAYMENT`
  booking may sit un-cancelled until `Sweep Stale Holds` catches it on its next scheduled run —
  a UX/operational gap, not a correctness one, since no seat is ever double-sold as a result
  (the provider's own TTL already released the seat; `booking-service` is just slow to notice).
- **Payment-event lag or loss**, once `payment-service` exists, is the one category on this list
  with real correctness stakes — `docs/architecture/high-level-design.md` §6's saga/outbox
  treatment exists specifically to bound this risk, not eliminate the need to think about it.

## What `booking-service` Deliberately Does Not Consume

- **`ReviewSubmitted`** — `search-service`'s and `analytics-service`'s concern, not
  `booking-service`'s. `booking-service` is the one being *called* by `review-service`
  (`use-cases.md`'s "Verify Booking"), not a consumer of anything review-related.
- **Any event `inventory-service` re-publishes purely for `search-service`'s benefit**
  (`RouteUpdated`, `OperatorUpdated`, `FareSnapshotUpdated`) — see "From `inventory-service`"
  above.
- **Refund-lifecycle events** (`RefundInitiated`/`RefundCompleted`/`RefundFailed`) — these are
  `payment-service`-internal-to-`analytics-service`/`notification-service` signals
  (`docs/architecture/event-catalog.md`). `booking-service` requests a refund and moves on; it
  does not track the refund's own progress as part of its state machine (`domain-model.md`'s
  "Reconciling the Requested State Vocabulary" — `REFUNDED` row).
