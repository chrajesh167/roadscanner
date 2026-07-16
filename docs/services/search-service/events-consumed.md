# Search Service — Events Consumed

Per `docs/architecture/event-catalog.md`'s delivery model, applied here without restatement of the general rules (see that document for the full explanation): all events below arrive **at-least-once** and are only ordered *within* a partition keyed by trip id — `search-service` must treat every handler below as idempotent and must not assume cross-trip ordering.

## From `operator-service`

| Event | Purpose for `search-service` | Handling Notes |
|---|---|---|
| `TripPublished` | A new trip becomes searchable — create its `SearchableTrip` projection | Upsert keyed by trip id — a redelivery is a harmless overwrite, never a duplicate (`use-cases.md`, `sequence-diagrams.md` §1) |
| `TripUpdated` | Fare, schedule, or capacity changed on an already-indexed trip — overwrite the affected fields | Same idempotent-upsert handling as `TripPublished`; if the projection doesn't exist yet (an out-of-order delivery relative to `TripPublished`), treat this as a create — see "Ordering Edge Case" below |
| `TripCancelled` | The trip must stop appearing as bookable | Idempotent: applying this twice to an already-cancelled projection is a no-op, matching `docs/architecture/event-catalog.md`'s general "at-least-once" note and the same pattern `SeatReleased` uses elsewhere on this platform |

**Failure considerations, restated for this service specifically:** per `event-catalog.md`, if `search-service` is behind on this topic, a trip is simply not yet searchable (or a fare change not yet visible) — no data is lost, and it self-heals as soon as the consumer catches up. `TripCancelled` is the one row worth watching operationally: a lagging consumer here means a cancelled trip still shows as bookable in search results for longer than intended. This is a UX staleness issue, not a correctness one — `inventory-service` and `booking-service` (both direct `TripCancelled` consumers in their own right, per `event-catalog.md`) are what actually prevent a booking against a cancelled trip; `search-service` lagging here cannot cause an inconsistent booking, only a confusing search result.

**Ordering edge case:** because ordering is only guaranteed within a partition (keyed by trip id per `event-catalog.md`), `TripUpdated` or `TripCancelled` for a given trip will never arrive before that trip's own `TripPublished` — they share a partition key. `search-service`'s handlers do not need to defend against out-of-order delivery across these three event types for the *same* trip; they do need to defend against redelivery (idempotency, covered above).

## From `review-service`

| Event | Purpose for `search-service` | Handling Notes |
|---|---|---|
| `ReviewSubmitted` | Recompute the `RatingSnapshot` for the reviewed trip/operator | No ordering or idempotency risk beyond the general model — per `event-catalog.md`, "reviews are not on any latency- or consistency-critical path" |

## What `search-service` Deliberately Does Not Consume

- **`SeatHeld` / `SeatReleased`** (from `inventory-service`) — per `event-catalog.md`, these are consumed only by `booking-service` and `analytics-service`. Live seat-count freshness is handled by a synchronous cached call instead — see `boundaries.md` for the full reasoning on why this data is intentionally *not* event-sourced.
- **`BookingCreated` / `BookingConfirmed` / `BookingCancelled`** (from `booking-service`) — `search-service` has no relationship with the booking lifecycle at all (`boundaries.md`); a booking's effect on availability is already reflected the next time the cached `inventory-service` call runs.
- **Payment events** — entirely outside this service's concern (`responsibilities.md`).
