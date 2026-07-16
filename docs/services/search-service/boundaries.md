# Search Service — Boundaries

This deepens `docs/architecture/service-boundaries.md`'s `search-service` entry and `docs/architecture/database-ownership.md`'s read-model pattern with the specific decisions this service itself must make, rather than restating what those documents already cover platform-wide.

## The Central Design Point: Two Different Freshness Strategies, One Service

`search-service`'s index is built from **two upstream services, on purpose held to two different consistency models**, because the two kinds of data they provide have fundamentally different change characteristics:

| Data | Source | How it reaches `search-service` | Freshness |
|---|---|---|---|
| Trip shape — route, schedule, fare, operator, bus type | `operator-service` | Kafka events (`TripPublished`, `TripUpdated`, `TripCancelled`) — see `events-consumed.md` | Eventually consistent; changes rarely (an operator edits a schedule), so event-propagation lag (typically sub-second) is invisible in practice |
| Live seat availability count | `inventory-service` | Synchronous query, fronted by a short-TTL Redis cache (`docs/architecture/high-level-design.md` §7) | As fresh as the cache TTL allows; deliberately *not* pushed via events (`inventory-service` does not emit anything `search-service` consumes — see below) |
| Aggregate rating | `review-service` | Kafka event (`ReviewSubmitted`) | Eventually consistent; changes are naturally infrequent per trip/operator |

**Why availability isn't event-sourced like the rest:** `docs/architecture/event-catalog.md`'s Seat Inventory Events (`SeatHeld`, `SeatReleased`) are consumed only by `booking-service` and `analytics-service` — never `search-service`. A seat's availability count changes on every hold, release, and booking across every active trip platform-wide; treating it as an event stream `search-service` must replay into its own index would mean maintaining a per-seat write-amplified copy of exactly the data `inventory-service` is already tuned to serve, for no accuracy benefit (holds are TTL'd in seconds — an event-derived copy would be stale within one hold's lifetime anyway). A cached synchronous read is the honest design for data this volatile: it's *always at most one cache TTL stale*, which is a tighter and more honest bound than an event-replay lag would be, at a fraction of the plumbing cost.

**Why trip shape *is* event-sourced instead of also being a live call:** the opposite reasoning applies — trip/schedule/fare data changes rarely, but every single search query needs it, and a search is a fan-out across potentially hundreds of trips per query. A live call to `operator-service` per trip per search would violate NFR-1 (~2s p95) the moment result sets get large. Denormalizing it locally, kept current via events, is what makes a single search query a single local read instead of an N-way service fan-out.

## Relationship to `operator-service`

One-directional: `search-service` consumes `operator-service`'s trip lifecycle events (`events-consumed.md`) and never calls it synchronously. `operator-service` has no reason to know `search-service` exists — this is the read-model pattern in its purest form (`database-ownership.md`).

## Relationship to `inventory-service`

The one synchronous dependency this service has. Scoped narrowly: `search-service` calls `inventory-service`'s "Trip Availability Query" category (`docs/architecture/api-inventory.md`) only for a seat **count** to display and rank by — never for seat-level detail, never to place or validate a hold. This call sits behind the short-TTL cache from `high-level-design.md` §7, which exists specifically to absorb read-heavy search load without turning every search query into a live hit against `inventory-service`'s database.

**Failure mode:** if `inventory-service` (or the cache) is unavailable, search must **degrade, not fail** — return results with availability omitted or marked "unknown" rather than failing the whole search. Availability is an enrichment of an already-useful result set (route, schedule, fare are still correct), not a precondition for the result set to exist. This is a deliberate asymmetry with `docs/architecture/seat-locking-flow.md`'s "fail closed" rule for seat holds — that rule protects a correctness guarantee (no overselling); this path has no correctness guarantee to protect, only a display nicety, so it fails open.

## Relationship to `review-service`

Search holds only what `events-consumed.md` describes: a denormalized aggregate rating, kept current via `ReviewSubmitted`. It never calls `review-service` synchronously and never stores individual review text — full review content is `review-service`'s surface, reached directly (`docs/architecture/api-inventory.md`).

## Relationship to `booking-service` and `payment-service`

None. `search-service` is not on the booking or payment critical path in either direction — it produces a result a traveler acts on next (typically a hold via `inventory-service`), but has no synchronous or asynchronous relationship with either service. This is worth stating explicitly because it's easy to assume a "trip becomes unavailable" signal should flow booking → search; it doesn't need to, because availability is never event-sourced into search's index in the first place (see above) — the next cached read of `inventory-service` reflects it naturally.

## Relationship to `customer-web`

The only client. Reached exclusively through `api-gateway`, like every other client-facing service (`high-level-design.md` §2). `customer-web` also calls `inventory-service` directly for the trip-detail/seat-map view (`api-inventory.md`) — `search-service`'s responsibility ends at producing the ranked list; it does not proxy the detail view.

## What's Deliberately Out of Scope

- **Seat-map / seat-level detail** — `inventory-service` + `customer-web`, direct (see `responsibilities.md`).
- **A "search-service publishes results changed" event** — nothing downstream needs one. No service's correctness depends on knowing `search-service`'s index state; only `customer-web`'s next query does, and that's satisfied by the query itself. See `events-published.md`.
- **Cross-vertical (train/flight/hotel) search** — Phase 2+, per `high-level-design.md` §12; this phase's read model is bus-only by construction, not by an artificial restriction that will need removing later.
