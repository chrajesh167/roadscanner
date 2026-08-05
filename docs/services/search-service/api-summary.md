# Search Service — API Summary

This describes the service's operations at the **category level** — purpose and conceptual inputs/outputs — per `docs/architecture/api-inventory.md`'s convention. It deliberately stops short of HTTP verbs, paths, status codes, and field-level JSON schema: those are published as OpenAPI by the running service at `/v3/api-docs`, which is generated from the controllers and therefore cannot drift from them the way a hand-written table can.

## Traveller-facing operations

| Category | Purpose | Conceptual Input | Conceptual Output | Notes |
|---|---|---|---|---|
| Trip Search | Search trips by origin, destination, and travel date | Origin, destination, travel date | Ranked, paged list of trips (operator, times, duration, bus type, fare, live seat availability, rating) | FR-2.1, FR-2.2. Optionally federates live provider results when both canonical location ids are supplied |
| Filter & Sort | Refine an in-progress search | Price range, departure-time window, bus type, minimum rating, sort key — passed alongside Trip Search's input, not a separate call | Same shape as Trip Search, filtered/reordered | FR-2.3. One operation with parameters, not a distinct request against a previously-returned result set |
| Trip Detail | Resolve one indexed trip, with its live availability overlay | Trip id | The indexed trip plus current availability | Availability degrades honestly: an unreachable `inventory-service` yields "availability unknown", not a failed request |
| Place Suggestions | Autocomplete over the origins/destinations of bookable indexed trips | Name fragment | Matching place names | Derived from the index, so it only ever suggests places that have trips |
| Location Autocomplete | Autocomplete over the canonical location catalogue | Name fragment | Compact location summaries, carrying the canonical id | The id returned here is the only location identifier other services accept |
| Location Lookup | Resolve one canonical location by id | Canonical location id | The full catalogue entry | Withdrawn locations still resolve, so historical references stay meaningful |
| External Place Autocomplete | Suggest places from an external provider, annotated with catalogue identity | Name fragment | Candidate places, each flagged as curated or not | Read-only enrichment. Never adds to the catalogue and never creates a provider mapping |

## Administrative operations

Gated on `ROLE_ADMIN` at this service, not only at `api-gateway` — the catalogue is data the whole platform resolves against, so the gate belongs where the write lands.

| Category | Purpose | Conceptual Input | Conceptual Output | Notes |
|---|---|---|---|---|
| Location Authoring | Create, replace, and withdraw canonical catalogue entries | Display name, city/state/country, optional coordinates, Google place id, timezone | The resulting catalogue entry | Replace is a full replace: an omitted optional field clears it. Withdrawal is a soft delete — the row is retained because trips and provider mappings reference it |
| Provider Mapping Administration | Author the translation layer between canonical locations and one provider's own vocabulary | Canonical location, provider code, the provider's city/station identifiers, optional opaque metadata, verified flag | The mapping, with its canonical location resolved alongside it | Create/replace/delete plus a filtered, paged listing. Deletion is a hard delete: a mapping is a translation rule with nothing referring to it, and a wrong translation should stop existing rather than linger |
| Unmapped-Location Worklist | List canonical locations a given provider cannot yet express | Provider code, optional search term | Active locations with no mapping for that provider | The complement of the mapping listing — "what does this provider still need?" |
| Index Rebuild | Discard the search index and replay retained Kafka history | — | Accepted; repopulation proceeds in the background | Operational, not part of the client-facing API |

### Why the provider-mapping reads are admin-gated too

Every other read on this service is anonymous. These are not, because they are the only responses in the platform that carry a provider's own identifiers. Keeping provider vocabulary out of traveller-facing contracts is the reason the location module exists (`boundaries.md`), so the gate covers the whole surface rather than the writes alone — a public read here would undo the containment in one line. Nothing in a traveller-facing response or schema names a provider identifier; `LocationCatalogueEndToEndTest` asserts that per schema against the published spec.

Mapping administration never creates a canonical location. An operator maps places that already exist, authored through Location Authoring or promoted from an external place suggestion; letting a provider's vocabulary mint RoadScanner places would invert the direction the catalogue is authored in.

## What's Deliberately Absent

- **Seat-map / seat-level availability.** Per `docs/architecture/api-inventory.md`, `inventory-service`'s "Trip Availability Query" is consumed directly by `customer-web`'s trip-detail view as well as by `search-service` internally (`boundaries.md`) — there is no `search-service` endpoint that proxies seat-level detail.
- **Booking, holds, and review submission.** No transactional write exists on this service. The writes it does expose are catalogue and mapping administration — reference data this service owns, not steps in a traveller's journey.
- **Rating/review detail retrieval.** Results carry only the aggregate `RatingSnapshot` (`domain-model.md`); full review content is fetched from `review-service` directly.
- **A provider registry.** Provider configuration, credentials, sessions, and health belong to `provider-integration-service`, the only service permitted to call a provider directly (`docs/architecture/service-boundaries.md`). This service holds the location↔provider translation table and nothing else about a provider; the provider code on a mapping is an open string, so onboarding one is a registration there plus rows here — no code change in either.

## Consumers

- `customer-web`, via `api-gateway` — the traveller-facing operations.
- `admin-console` is the intended consumer of the administrative operations. It does not call them yet: the API landed first, and the console still covers only the provider registry in `provider-integration-service`. The surface is nonetheless built for a browser — CORS allows the full method set, not just the public read half, because a missing method fails the preflight and makes an otherwise correct endpoint unreachable from any browser.
- No other service calls `search-service`. Translating a canonical location into provider identifiers happens *inside* this service, behind the in-process `GetProviderMapping` port that its own provider-search path uses; it is not, and should not become, a route.
