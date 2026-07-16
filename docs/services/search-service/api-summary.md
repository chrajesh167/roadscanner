# Search Service — API Summary

This describes the service's public operation at the **category level** — purpose and conceptual inputs/outputs — per `docs/architecture/api-inventory.md`'s convention. It deliberately stops short of HTTP verbs, paths, status codes, and field-level JSON schema — those are written as an OpenAPI spec (`docs/api/`) when implementation begins.

## Operations

| Category | Purpose | Conceptual Input | Conceptual Output | Notes |
|---|---|---|---|---|
| Trip Search | Search trips by origin, destination, and travel date | Origin, destination, travel date | Ranked, paged list of trips (operator, times, duration, bus type, fare, live seat availability, rating) | FR-2.1, FR-2.2 |
| Filter & Sort | Refine an in-progress search | Price range, departure-time window, bus type, minimum rating, sort key — passed alongside Trip Search's input, not a separate call | Same shape as Trip Search, filtered/reordered | FR-2.3. Matches `docs/architecture/api-inventory.md`'s two listed categories for this service; presented here as one operation with parameters, since a traveler's filters and sort are part of the same query, not a distinct request against a previously-returned result set |

That is the complete client-facing surface — see `responsibilities.md` and `boundaries.md` for what is deliberately not exposed here.

## What's Deliberately Absent

- **Seat-map / seat-level availability.** Per `docs/architecture/api-inventory.md`, `inventory-service`'s "Trip Availability Query" is consumed directly by `customer-web`'s trip-detail view as well as by `search-service` internally (`boundaries.md`) — there is no `search-service` endpoint that proxies seat-level detail.
- **Any write operation.** No booking, hold, or review-submission endpoint exists on this service, ever — `search-service` is read-only in its entirety (`responsibilities.md`).
- **A "get trip by id" lookup.** Not listed in `docs/architecture/api-inventory.md`'s categories for this service, and not needed: a specific trip's detail is served by `inventory-service` directly once a traveler has selected it from search results.
- **Rating/review detail retrieval.** `search-service`'s results carry only the aggregate `RatingSnapshot` (`domain-model.md`); full review content is fetched from `review-service` directly, not through this service.

## Consumers

Per `docs/architecture/api-inventory.md`: `customer-web`, via `api-gateway` only — no other client or service calls `search-service`.
