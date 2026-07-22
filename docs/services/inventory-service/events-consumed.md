# Inventory Service — Events Consumed

Per `docs/architecture/event-catalog.md`'s delivery model: at-least-once, ordered only within a
partition keyed by the relevant entity id. Every handler below is idempotent and assumes no
cross-entity ordering.

## From `operator-service` — the only producer this service consumes from

| Event | Purpose for `inventory-service` | Handling Notes |
|---|---|---|
| `TripPublished` | Ingest a new first-party trip: `Trip` (supply origin `FIRST_PARTY`), `TripSchedule`, `SeatLayout` | Upsert keyed by trip id |
| `TripUpdated` | Overwrite shape fields (route, schedule, fare) | Never touches `SeatLayout` shape once materialized, or any live seat concept — there is none here |
| `TripCancelled` | Mark the `Trip` unbookable | Idempotent, terminal |
| `RouteUpdated` | Update the referenced `Route` | New — needs adding to `docs/architecture/event-catalog.md`'s Trip Management Events |
| `OperatorUpdated` | Update denormalized operator display fields | New — same |

**Failure considerations:** if `inventory-service` is behind on any of these, the effect is a
catalog staleness window — a trip not yet visible, a fare not yet current. No correctness
guarantee is at risk (that guarantee lives entirely in `provider-integration-service` now), so
consumer lag here is an operational/UX metric, not a paging-worthy one, unlike `TripCancelled` in
the old model where it also gated hold-release correctness.

## What `inventory-service` Deliberately Does Not Consume

- **Anything from `provider-integration-service`.** `inventory-service` *calls*
  `provider-integration-service` synchronously (the availability facade and catalog
  synchronization) — it does not subscribe to its events (`ProviderUnavailable`,
  `ProviderRecovered`, `SessionExpired`). Those are `provider-integration-service`'s own
  operational signals, relevant to `booking-service`/`analytics-service`, not to catalog
  correctness.
- **`BookingConfirmed`/`BookingCancelled`.** The old version of this document consumed these to
  convert holds into allocations — that entire responsibility is gone. `inventory-service` has no
  reaction to a booking's outcome at all; it has nothing left to update when one occurs.
- **Payment events.** No relationship, direct or eventual, same as before.
- **`ReviewSubmitted`.** `search-service`'s concern, unchanged.
