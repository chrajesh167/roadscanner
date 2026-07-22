# Inventory Service — Data Ownership

## What This Service Owns

One Postgres database (`docs/architecture/database-ownership.md`), holding `City`, `Station`,
`Route`, `Trip`, `TripSchedule`, `SeatLayout`, `ProviderMapping`, `FareSnapshot`, and `SyncRecord`.
Redis, if used at all, is a **cache only** — see "The Exception This Service No Longer Has" below.

## Three Different Kinds of Authority, None of Them Live Seat State

| Data | Authority | Why |
|---|---|---|
| `City`, `Station`, `Route`, `ProviderMapping`, `SyncRecord` | **Fully authoritative — owned outright, no upstream source** | Originated here; no other service tracks structured geography, route definitions, or the provider-mapping/sync bookkeeping this service exists to maintain |
| `Trip`, `TripSchedule`, `SeatLayout`, `FareSnapshot` for first-party trips | **Kept-current copy**, not authoritative | Corrected by whatever `operator-service` publishes next — same relationship `search-service` already has |
| `Trip`, `TripSchedule`, `SeatLayout`, `FareSnapshot` for provider-sourced trips | **Owned, but sourced from an external system inventory-service doesn't control** | Reconciled by catalog synchronization against `provider-integration-service`; "authoritative" here means "the canonical RoadScanner record," not "immune to drift from the provider's own changes" — the sync process is what keeps drift bounded, not a guarantee against it ever happening |
| Live seat availability, holds, reservations | **Never owned here, in any form** | See below |

## The Exception This Service No Longer Has

The previous version of this document carved out a deliberate exception to
`docs/architecture/high-level-design.md` §7's "Redis is always expendable" rule, for an
active seat hold. **That exception has moved with the responsibility that created it** — it now
belongs entirely to `provider-integration-service`'s data-ownership story, not this service's.
`inventory-service` has no comparable exception: if it uses Redis at all (e.g., to cache a
catalog lookup), that cache is unconditionally expendable, exactly as §7 states platform-wide with
no carve-out. This is worth stating explicitly, because it's the one sentence in the old document
most likely to be copy-pasted forward incorrectly if this review didn't call it out by name.

## Retention

Catalog rows persist for as long as the trip/route/operator they describe is relevant — no
special retention policy beyond what a config-like dataset normally needs. `SyncRecord` history
can be pruned on a simple schedule; unlike a booking or payment record, there is no compliance
reason to retain it indefinitely.

## Rebuildability

First-party catalog data is rebuildable by replaying `operator-service`'s retained event history,
the same payoff `search-service`'s index gets from the same design (`database-ownership.md`).
Provider-sourced catalog data is rebuildable by re-running catalog synchronization — the source of
truth for *that* half of the catalog is each provider's own system, reachable at any time through
`provider-integration-service`, not a Kafka log RoadScanner would need to replay.

## Explicitly Not Designed Here

Physical Postgres schema, the exact provider-trip-matching heuristic catalog synchronization uses,
Redis key structure for any cache this service ends up needing, and the seat-layout-snapshot
format embedded in `TripPublished` — all implementation decisions made when this service and its
upstream event producers are built.
