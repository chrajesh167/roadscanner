# Search Service — Domain Model

This is a conceptual model of what `search-service` holds — shapes and value objects, not SQL or a specific search-engine schema. Physical schema (Postgres full-text, a dedicated search engine, or otherwise) is an implementation decision made when the service is built, not designed here — same convention as `docs/services/auth-service/database-design.md`'s "physical schema... not designed here."

## Why This Isn't a DDD Aggregate Model

Every other service's domain model (see `docs/services/auth-service/domain/model` for the pattern) centers on aggregates with lifecycle rules and invariants this service alone enforces — a `Credential` decides for itself whether a login attempt is allowed. `search-service` has no equivalent. Its central concept, `SearchableTrip`, has no business invariant to protect and no state transition it initiates: every field is either copied from an upstream event or overwritten wholesale by the next one. There is no "can this trip be searched" business rule living here — bookability is `operator-service`'s and `inventory-service`'s decision, reflected into this shape, not decided by it.

This is a deliberate and honest distinction, not a gap: a read-model service's "domain model" is a set of read-optimized **projections**, not an aggregate root with encapsulated behavior. Modeling it as if it had aggregate-style invariants would be pretending this service has authority it deliberately does not have (`responsibilities.md`).

## Core Concepts

### SearchableTrip (the read-model projection)

The unit of a search result. Conceptually holds:

- a trip identifier — the same id `inventory-service`/`operator-service` use, so a search result can be acted on (held, booked) without translation
- operator identifier and display name, bus type, amenities — denormalized snapshot from `operator-service`'s fleet/route data at the time of the last `TripPublished`/`TripUpdated` event
- route: origin, destination
- schedule: departure time, arrival time, duration
- fare — the current published fare snapshot
- a bookability flag — set false on `TripCancelled`, distinct from availability (see below): a cancelled trip is never bookable regardless of seats; an available-seats trip with zero seats left is bookable-in-principle but currently full
- `ratingSnapshot` — see below
- a **freshness marker** (last-updated timestamp, keyed to the source event) — not shown to the traveler, but essential for reasoning about propagation lag and for safely discarding an out-of-order event (`events-consumed.md`)

**Deliberately not held:** live seat availability count. That is fetched at query time, not stored in the projection, because it changes far faster than the projection's own event-driven refresh cycle — see `boundaries.md`'s freshness-strategy table. Storing a number this volatile in the index would just mean serving a wrong number with false confidence instead of an explicitly-labeled live overlay.

### RatingSnapshot

A denormalized aggregate, not a review list: average rating and review count for a trip/operator, updated by `ReviewSubmitted` (`events-consumed.md`). Holds no review text, no reviewer identity — `review-service` remains the only place that data lives (`responsibilities.md`).

### Route (value object)

Origin and destination, as a pair — the primary key of "what is a traveler searching for," independent of any specific trip.

### FareSnapshot (value object)

The fare `search-service` last observed for a trip via `TripPublished`/`TripUpdated`. Explicitly a snapshot, not a live price feed — if `operator-service` changes a fare between this snapshot and a traveler's actual hold/booking attempt, the authoritative fare check happens where it always does, downstream at hold/booking time; this snapshot exists to rank and display, not to be trusted as a final price.

### SearchQuery (value object)

The traveler's ask: origin, destination, travel date, plus optional filter/sort parameters (price range, departure-time window, bus type, minimum rating, sort key) — see `use-cases.md` and `api-summary.md`. A pure input shape; carries no persistence or identity.

### SearchResultPage (value object)

A ranked, paged list of `SearchableTrip` projections, each overlaid with a live availability figure (or an explicit "unavailable/unknown" marker per `boundaries.md`'s degrade-not-fail rule) at the moment the query executed. Never persisted — constructed fresh per query.

## Summary Table

| Concept | Kind | Source of Truth Elsewhere | Kept Current Via |
|---|---|---|---|
| `SearchableTrip` | Read-model projection | `operator-service` (shape), `inventory-service` (bookability signal via cancellation) | `TripPublished` / `TripUpdated` / `TripCancelled` events |
| `RatingSnapshot` | Read-model projection (embedded in `SearchableTrip`) | `review-service` | `ReviewSubmitted` events |
| `Route`, `FareSnapshot` | Value objects (embedded) | `operator-service` | Same events as `SearchableTrip` |
| `SearchQuery`, `SearchResultPage` | Query-time value objects | N/A — never persisted | Constructed per request |
| Live availability count | Query-time overlay, not stored | `inventory-service` | Synchronous cached call (`boundaries.md`) — never an event |
