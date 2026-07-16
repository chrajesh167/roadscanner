# Search Service — Use Cases

Two kinds of use case live here: **client-facing** (triggered by a traveler's request, via `api-gateway`) and **internal/event-driven** (triggered by the platform's event stream, maintaining the index no client ever calls directly). See `domain-model.md` for the shapes these operate on.

## Client-Facing

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Search Trips** | Traveler submits origin, destination, travel date, optional filters/sort (`SearchQuery`) | Query the local index for matching, bookable `SearchableTrip` projections; apply filters and sort; overlay live availability per trip via the cached `inventory-service` call; return a ranked `SearchResultPage` | The one primary client-facing use case (FR-2.1–FR-2.3). Filtering and sorting are parameters of this same use case, not separate ones — see `api-summary.md` |

That is the entire client-facing surface. `search-service` intentionally exposes nothing else to a client — no seat-map lookup, no booking initiation, no review submission (`responsibilities.md`, `boundaries.md`).

## Internal (Event-Driven Index Maintenance)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Index a Newly Published Trip** | `TripPublished` (from `operator-service`) | Create a new `SearchableTrip` projection from the event payload | See `sequence-diagrams.md` |
| **Update an Indexed Trip** | `TripUpdated` (from `operator-service`) | Overwrite the affected fields (fare, schedule, capacity) on the existing projection | Idempotent by construction — applying the same update twice yields the same projection state (`events-consumed.md`) |
| **Remove/Flag a Cancelled Trip** | `TripCancelled` (from `operator-service`) | Mark the projection's bookability flag false (or remove it from query results) | Must be a no-op if already applied — `events-consumed.md`'s at-least-once delivery model |
| **Update a Rating Snapshot** | `ReviewSubmitted` (from `review-service`) | Recompute or increment the trip/operator's `RatingSnapshot` | Does not touch review content — only the aggregate (`domain-model.md`) |

## Operational (Not Triggered by Any Message)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Rebuild Index** | Manual/operational (index loss, corruption, a ranking-algorithm change requiring reprocessing) | Replay the retained event history from `operator-service` and `review-service` topics from the earliest available offset, reconstructing every projection from scratch | Only possible because the index is a strictly derived, disposable copy (`docs/architecture/database-ownership.md`) — this is the operational payoff of that design choice, not a hypothetical. Exact replay tooling is an implementation decision |

## What's Deliberately Not a Use Case Here

**Live availability lookup** is not modeled as its own use case — it's a step inside "Search Trips" (the cached call to `inventory-service` described in `boundaries.md`), not a capability `search-service` exposes independently. There is no "check seat availability" endpoint on this service; that's `inventory-service`'s own API, called by `customer-web` directly for the trip-detail view (`api-inventory.md`).
