# Inventory Service

RoadScanner's catalog and metadata service. It owns Cities, Stations, Routes, Operators, Trip
metadata, Provider mappings, Schedule metadata, Fare snapshots, and Synchronization metadata —
and, by explicit, reviewed architectural decision, it **never** owns live seat availability, seat
locks, seat reservations, booking state, tickets, provider authentication, or provider sessions
(those belong to `provider-integration-service` and `booking-service`). See
[`docs/services/inventory-service/overview.md`](../../../docs/services/inventory-service/overview.md)
and the rest of that directory — frozen after two architecture reviews — for the full design.

**Status: feature-complete for Phase 1.** Domain, application use cases, persistence
(Postgres/Flyway), Kafka inbound (from `operator-service`) and outbound (merged catalog events)
adapters, the live-availability facade over `provider-integration-service`, and the REST surface
with OpenAPI documentation are all implemented. See "API Surface" and "Remaining Integration
Points" below.

## API Surface

Public catalog API, under `/api/v1/inventory` (see `/swagger-ui.html` for the full contract):

| Endpoint | Purpose |
|---|---|
| `GET /cities?q=&limit=` | Prefix-search catalog cities, for search-form autocomplete |
| `GET /stations?cityId=` | Browse stations for a city |
| `GET /trips/{tripId}` | Catalog trip metadata — route, schedule, operator, fare, bookable flag |
| `GET /trips/{tripId}/seat-layout` | Static seat layout — numbering/deck/type only, never live status |
| `GET /trips/{tripId}/availability` | **Frozen contract.** Live seat count, proxied from `provider-integration-service`; `search-service`'s `AvailabilityClient` already depends on this exact path and `{"availableSeats": N}` shape. Returns `503` (not a `200` sentinel) when unknown. |
| `GET /trips/{tripId}/provider-mapping` | The `(providerType, providerTripId)` bridge for a trip, if any |
| `GET /sync/status` | Most recent `SyncRecord` per configured provider |

## Requirements

- Java 21
- Docker (for local Postgres/Kafka via `docker-compose.yml` at the repo root, and for
  Testcontainers-backed integration tests). No Redis — this service uses none; see
  "Architectural Decisions".

No local Maven install required — use the wrapper (`./mvnw`).

## Running Locally

```bash
# from the repo root: start this service's Postgres, plus the shared Kafka
docker compose up -d postgres-inventory kafka

# from this directory: run the app natively against them
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The app starts on `:8084` with the `local` profile, expecting `provider-integration-service` to
already be running natively on `:8083` (see that service's own README) for the availability
facade to return known results — if it isn't running, `/trips/{tripId}/availability` still
responds correctly with `503` (degrade, not fail). Useful local endpoints once running:
- `http://localhost:8084/actuator/health`
- `http://localhost:8084/swagger-ui.html`

Seed geography ships via Flyway (`V2__seed_catalog_geography.sql`) — Mumbai, Pune, Bengaluru,
Chennai, Delhi, Hyderabad and the routes between them, matching the demo routes already used in
`search-service`'s and `provider-integration-service`'s own READMEs:

```bash
curl -s "http://localhost:8084/api/v1/inventory/cities?q=Mum" | jq .
```

Trip catalog data arrives via Kafka from `operator-service` (`TripPublished`/`TripUpdated`/
`TripCancelled` on the `trip-events` topic) or via scheduled catalog synchronization against
`provider-integration-service`'s Mock provider — there is no REST write path, matching this
service's read-oriented catalog role.

### Running Fully Containerized

```bash
docker build -t roadscanner/inventory-service .
docker run --network host -e SPRING_PROFILES_ACTIVE=local -p 8084:8084 roadscanner/inventory-service
```

(`docker-compose.yml` intentionally does not include `inventory-service` itself — same rationale
as `auth-service`/`search-service`/`provider-integration-service`'s identical omission.)

## Building & Testing

```bash
./mvnw compile   # compiles cleanly
./mvnw test       # requires Docker running (Testcontainers — Postgres + Kafka)
./mvnw package    # builds the executable jar
```

`./mvnw test` runs the full suite: framework-free domain unit tests (`Trip`'s
ingest/update/cancel lifecycle including staleness/terminal-state rejection, `SeatLayout`,
`Route`, `ProviderMapping`, `SyncRecord`, `ProviderType`'s open-VO normalization),
application-layer tests against in-memory fakes and a fully-configurable
`StubProviderIntegrationClient` (including catalog-sync reconciliation's create-vs-update
branches and its failure path), a Kafka-listener dispatch test proving `TripEventListener` maps
each `operator-service` event type to the correct inbound-port command, a wire-shape test proving
`CatalogTripEventMessage` stays field-for-field identical to `search-service`'s shipped
`TripEventMessage`, Testcontainers-backed integration tests for the `Trip`/`ProviderMapping`/
`City` persistence adapters (the last against the real Flyway-seeded geography), a `@WebMvcTest`
for the frozen `AvailabilityController` contract, and `InventoryServiceEndToEndTest` driving the
Kafka-ingestion-to-REST-query flow over real HTTP.

*(In the environment this service was built in, Docker was unavailable, so the 31 tests requiring
no Docker — domain, application-layer, the Kafka dispatch/shape tests, and the `@WebMvcTest`
suite — were run and pass; the 17 Testcontainers-backed tests (3 persistence adapter classes, 1
end-to-end class) compile cleanly and fail solely with `Previous attempts to find a Docker
environment failed` — no other error — confirming they are blocked only by the missing Docker
daemon, not by any code defect. They follow the exact same `TestcontainersConfiguration`/
`@ServiceConnection` pattern `auth-service`/`search-service`/`provider-integration-service`
already use successfully in CI, so they're expected to pass under normal Docker availability —
run `./mvnw test` locally to confirm.)*

Docker Desktop is the assumed container runtime — Testcontainers and `docker compose` both work
with it out of the box, no extra environment configuration needed.

## Configuration Profiles

| Profile | Use | Datasource / Kafka / provider-integration-service |
|---|---|---|
| `local` | Running on a developer machine | `localhost`, matching `docker-compose.yml`; PIS base URL `http://localhost:8083` |
| `dev` | Deployed dev environment | Every value from an env var, no hardcoded fallback |
| `test` | Test execution | Only non-infrastructure properties; datasource/Kafka come from Testcontainers via `@ServiceConnection`; PIS base URL points nowhere reachable, deliberately exercising the "degrade, not fail" path |

No profile is active by default — a missing profile should fail loudly, matching every other
service's convention.

## Architectural Decisions

**Catalog vs. live state is a hard, reviewed split — not a convenience distinction.** This
service is the platform's canonical, provider-agnostic catalog (Postgres-backed, event-driven
from `operator-service` plus scheduled catalog synchronization); `provider-integration-service`
owns everything live (seat status, sessions, provider auth). `search-service` never learns which
provider produced a trip — that fact stops at `ProviderMapping`, which is not exposed on any
trip-facing response.

**Two supply sources reconcile into one canonical `Trip`, keyed on identity, not fuzzy matching.**
`operator-service`'s first-party trips arrive via Kafka (`SupplyOrigin.FIRST_PARTY`);
provider-discovered trips arrive via scheduled `SynchronizeProviderCatalog` runs
(`SupplyOrigin.PROVIDER_SYNCED`). Provider-sourced reconciliation keys strictly on
`(providerType, providerTripId)` via `ProviderMapping` — no cross-matching against first-party
trips is attempted, a documented, deliberate simplification (see `ProviderMapping`'s Javadoc).

**`CatalogTripEventMessage` is deliberately, permanently field-for-field identical to
`search-service`'s already-shipped `TripEventMessage`.** This is what makes a future
topic-source config swap on `search-service`'s side (from `operator-service`'s topic directly to
this service's merged-catalog topic) a zero-code-change operation. Verified by
`CatalogTripEventMessageShapeTest`; do not add, remove, rename, or reorder fields on that record
without updating both that test and `docs/services/inventory-service/events-published.md`.

**`GET /trips/{tripId}/availability` degrades to `503`, never a `200` with a sentinel value.**
`search-service`'s already-shipped `AvailabilityClient` catches any `RestClientException` (any
non-2xx) and treats it as "unknown" on its own side — so this endpoint returning an error status
on an unknown result required no change to that already-running code. The same rule applies one
hop further down: `ProviderIntegrationClient`'s adapter never throws, only degrades.

**No Redis, anywhere in this service.** Nothing in the frozen architecture docs mandates a cache
here — this is a catalog service whose Postgres tables *are* the source of truth, not a live-state
proxy needing a TTL cache in front of a volatile upstream (that pattern belongs to
`provider-integration-service`). The one thing that could plausibly benefit from caching — the
PIS session used by `ProviderIntegrationClientAdapter` — uses an in-memory `ConcurrentHashMap`
instead, since it's per-instance, non-durable, and cheap to re-establish; a network hop to Redis
would add latency and an operational dependency for no correctness benefit.

**RFC 7807 `ProblemDetail` for error responses — a deliberate, disclosed deviation from the
platform's established custom `ErrorResponse` pattern**, done for this service specifically per
explicit implementation instruction. Every other RoadScanner service uses its own
`GlobalExceptionHandler`'s `ErrorResponse` record; this one uses Spring's `ProblemDetail`.

**No `backend/shared-libs` dependency yet.** Same reasoning and same deviation as every other
service's `pom.xml` — that module is still an empty placeholder directory.

**No Lombok, `ddl-auto: validate` never `update`.** Same conventions as every other service in
this codebase.

## Remaining Integration Points

Implemented but awaiting other platform components or a deliberate operational decision (tracked,
not forgotten):

- **`operator-service` doesn't exist yet**, so nothing publishes real `TripPublished`/
  `TripUpdated`/`TripCancelled`/route/operator events in a live environment today — every
  ingestion path is implemented and tested against hand-constructed Kafka messages
  (`TripEventListenerTest`, `IngestPublishedTripServiceTest`) and, in the end-to-end test, a
  real embedded producer.
- **Catalog synchronization discovers providers from this service's own config
  (`roadscanner.inventory.sync.provider-types`), not a "list enabled providers" endpoint on
  `provider-integration-service`** — that endpoint doesn't exist there. Documented as a
  deliberate, reviewed simplification in `CatalogSyncCoordinator`'s Javadoc, not a gap.
- **`IngestRouteUpdate` is acknowledgment-only** (logs, does not reconcile) — there is no
  specified mapping from `operator-service`'s per-operator route concept to this service's
  city-to-city `Route` entity; inventing one would be an undocumented architectural decision,
  which the implementation instructions explicitly disallow. Flagged, not silently guessed at.
- **Testcontainers-backed test suites were not executed in the environment this service was
  built in** (Docker was unavailable there) — see "Building & Testing" above.
- **Custom Prometheus counters** (catalog-sync success/failure rate, reconciliation latency,
  per-event-type ingestion counts) — standard HTTP/JVM/Kafka-consumer metrics are exposed now via
  Micrometer; business-specific counters are a follow-up, not a correctness gap.
- **`backend/shared-libs` adoption** — unchanged from the bootstrap decision above.

## Package Structure

Hexagonal architecture — see
[`docs/services/inventory-service/domain-model.md`](../../../docs/services/inventory-service/domain-model.md)
and [`boundaries.md`](../../../docs/services/inventory-service/boundaries.md) for the full
rationale.

- `domain/` — models (`City`, `Station`, `Route`, `OperatorRef`, `Trip`, `SeatLayout`,
  `ProviderMapping`, `SyncRecord`, ...), ports (`in`/`out`), exceptions (framework-free)
- `application/usecase/{availability,browse,ingestion,sync}` — inbound-port implementations
- `adapter/in/event` — `operator-service` trip/route/operator Kafka listeners and wire messages
- `adapter/in/rest/{city,station,trip,availability,providermapping,sync}` — controllers and DTOs,
  plus `exception/` (RFC 7807 global mapping) and `filter/` (correlation id)
- `adapter/out/persistence` — JPA entity/mapper/repo/adapter per aggregate
- `adapter/out/client` — `ProviderIntegrationClientAdapter`, the sole live-data pass-through
- `adapter/out/kafka` — the merged-catalog event publisher
- `config/` — Jackson, CORS, OpenAPI, persistence, Kafka, `provider-integration-service` client,
  use-case wiring, the catalog-sync scheduler
