# Search Service

Trip search, ranking, and filtering over a derived read model. Owns no business state — see
[`docs/services/search-service/overview.md`](../../../docs/services/search-service/overview.md)
and the rest of that directory for the full design.

**Status: feature-complete for Phase 1.** All layers are implemented: domain, application use
cases, persistence (Postgres/Flyway), Kafka index maintenance, the Redis-backed availability
cache, and the REST surface with OpenAPI documentation. See "API Surface" and "Remaining
Integration Points" below.

## API Surface

All client-facing endpoints under `/api/v1/search` (see `/swagger-ui.html` for the full
contract); one operational endpoint under `/internal/search`:

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /trips` | none | Search trips by origin/destination/date, with optional filter and sort query parameters (FR-2.1–FR-2.3) |
| `GET /trips/{tripId}` | none | Look up one indexed trip, overlaid with live availability |
| `GET /suggestions` | none | Autocomplete over indexed origin/destination place names |
| `POST /internal/search/reindex` | none — see "Remaining Integration Points" | Discards the index and replays retained Kafka history to rebuild it |

This service implements no authentication or authorization itself, per this project's explicit
scope — `api-gateway` is the platform's authentication boundary
(`docs/architecture/authentication-flow.md`).

## Requirements

- Java 21
- Docker (for local Postgres/Redis/Kafka via `docker-compose.yml` at the repo root, and for
  Testcontainers-backed integration tests)

No local Maven install required — use the wrapper (`./mvnw`).

## Running Locally

```bash
# from the repo root: start Postgres + Redis + Kafka
docker compose up -d

# from this directory: run the app natively against them
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The app starts on `:8082` with the `local` profile (`localhost` Postgres on `5433`, Redis on
`6379`, Kafka on `9092` — matching the ports `docker-compose.yml` exposes).

Useful local endpoints once running:
- `http://localhost:8082/actuator/health`
- `http://localhost:8082/swagger-ui.html`

Try it end-to-end without `inventory-service` or `operator-service` existing yet — publish a
`TripPublished`-shaped message directly:

```bash
echo '{"eventType":"PUBLISHED","tripId":"<a-uuid>","operatorId":"<a-uuid>",
"operatorName":"Demo Travels","origin":"Mumbai","destination":"Pune",
"departureTime":"2026-08-01T08:00:00Z","arrivalTime":"2026-08-01T12:00:00Z",
"busTypeCategory":"AC Sleeper","amenities":["WiFi"],"fareAmount":550.00,
"fareCurrency":"INR","occurredAt":"2026-07-01T00:00:00Z"}' | \
  docker exec -i roadscanner-kafka kafka-console-producer \
    --bootstrap-server localhost:9092 --topic trip-events

curl "http://localhost:8082/api/v1/search/trips?origin=Mumbai&destination=Pune&date=2026-08-01"
```

The result comes back with `"availabilityKnown": false` — the "degrade, not fail" rule
(`docs/services/search-service/boundaries.md`) in action, since `inventory-service` isn't
running to answer the live seat-count overlay.

### Running Fully Containerized

```bash
docker build -t roadscanner/search-service .
docker run --network host -e SPRING_PROFILES_ACTIVE=local -p 8082:8082 roadscanner/search-service
```

(`docker-compose.yml` intentionally does not include `search-service` itself — same rationale
as `auth-service`'s identical omission, see the comment at the top of that file.)

## Building & Testing

```bash
./mvnw compile   # compiles cleanly
./mvnw test       # requires Docker running (Testcontainers — Postgres + Redis + Kafka)
./mvnw package    # builds the executable jar
```

`./mvnw test` runs the full suite (109 tests): framework-free unit tests for the domain and
application layers (the bulk of the suite — `SearchableTrip`'s staleness/terminal-state
invariants, every indexer, the availability overlay), Testcontainers-backed integration tests
(the persistence adapter's full filter/sort/pagination/suggestion behavior against real
Postgres, the Redis cache, and `KafkaEventProcessingIntegrationTest` publishing real JSON to a
real Kafka broker), `MockRestServiceServer`-based tests for the `inventory-service` HTTP client,
`@WebMvcTest` controller tests, and `SearchServiceEndToEndTest` — full HTTP flows including the
`reindex` endpoint proven to genuinely truncate the index and repopulate it via Kafka replay,
not just clear state.

Docker Desktop is the assumed container runtime — Testcontainers and `docker compose` both work
with it out of the box, no extra environment configuration needed.

## Configuration Profiles

| Profile | Use | Datasource / Redis / Kafka |
|---|---|---|
| `local` | Running on a developer machine | `localhost`, matching `docker-compose.yml` |
| `dev` | Deployed dev environment | Every value from an env var, no hardcoded fallback for secrets |
| `test` | Test execution | Only non-infrastructure properties (e.g. CORS origins, `inventory-service` base URL placeholder); datasource/Redis/Kafka come from Testcontainers via `@ServiceConnection` |

No profile is active by default — a missing profile should fail loudly, not silently boot
against a guessed environment, matching `auth-service`'s convention.

## Architectural Decisions

**No `backend/shared-libs` dependency yet.** Same reasoning and same deviation as
`auth-service`'s pom.xml — `platform-bom`, `common-persistence`, `common-observability`,
`common-testing`, and `common-messaging` are still empty placeholder directories. This module
stands alone on `spring-boot-starter-parent`.

**Kafka consumers use explicit, type-bound `JsonDeserializer`s, not type-id headers.** Each of
the two topics gets its own `ConsumerFactory` bound directly to its message type
(`TripEventMessage`, `ReviewSubmittedMessage`) — see `config/KafkaConfig`'s Javadoc. This is
required for interoperability: `operator-service` and `review-service` are independently built
services this consumer cannot assume will ever emit Spring-Kafka-specific headers.

**`ConsumerSeekCallback` for on-demand replay must come from `registerSeekCallback`, not
`onPartitionsAssigned`.** The callback captured during partition assignment is only safe to
invoke from the consumer's own thread; calling it from an HTTP request thread throws
`ConcurrentModificationException` ("KafkaConsumer is not safe for multi-threaded access"). The
callback from `registerSeekCallback` is the one designed for cross-thread, on-demand use — see
`adapter/in/event/TripEventListener`'s Javadoc. This was caught by
`SearchServiceEndToEndTest.reindexTruncatesTheIndexAndKafkaReplayRepopulatesIt`, not by
inspection — a reminder that the reindex path needed a real end-to-end proof, not just a unit
test of `RebuildIndexService`'s orchestration.

**A hand-built `ConsumerFactory` bypasses `KafkaConnectionDetails` unless applied explicitly.**
Spring Boot's autoconfigured Kafka beans apply `@ServiceConnection` overrides (Testcontainers,
in tests) automatically; a custom `ConsumerFactory` bean does not unless the connection details
are read and applied to the properties map by hand — see `config/KafkaConfig`.

**Sort defaulting lives in exactly one place.** `SearchRankingPolicy` resolves a `null` sort to
`DEPARTURE_TIME_ASC`; the persistence adapter's `resolveSort` requires an already-resolved,
non-null `SortOption` and does not itself default one — avoiding the same rule existing in two
places that could quietly drift apart.

**Duration sorting uses a database-generated column, not a departure/arrival timestamp
comparison.** `duration_seconds` is `GENERATED ALWAYS AS (EXTRACT(EPOCH FROM (arrival_time -
departure_time))) STORED` (see the V1 migration) — sorting by arrival time alone would not
actually sort by trip duration for trips with different departure times.

**No Lombok.** Same reasoning as `auth-service`: Java 21 records and plain constructors cover
this service's code without an annotation-processing dependency.

**Hibernate `ddl-auto: validate`, never `update`.** Flyway is the sole source of schema truth in
every environment, including local.

## Remaining Integration Points

Implemented but awaiting other platform components or a deliberate operational decision
(tracked, not forgotten):

- **`inventory-service` doesn't exist yet.** The `AvailabilityClient` adapter is fully
  implemented and tested (`MockRestServiceServer`, plus live-in-the-running-service proof via
  the README's demo above) against a documented contract
  (`GET /api/v1/inventory/trips/{tripId}/availability` → `{"availableSeats": <int>}`), but there
  is nothing real to call yet — every live query currently degrades to
  `availabilityKnown: false`, which is correct, expected behavior per
  `docs/services/search-service/boundaries.md`, not a bug to fix later.
- **`operator-service` and `review-service` don't exist yet**, so nothing publishes to
  `trip-events`/`review-events` in a real environment today — the Kafka consumers are fully
  implemented and tested against hand-published messages matching the documented contract.
- **The `/internal/search/reindex` endpoint has no authentication or network-level
  restriction.** Per this project's explicit scope, no auth is implemented in this service at
  all; this endpoint's protection is expected to come from `api-gateway` never routing
  `/internal/**` publicly, or from a network-level restriction to internal callers only —
  neither is wired up yet since `api-gateway` doesn't exist yet either.
- **Finalized OpenAPI spec published to `docs/api/`** — the live spec is served at
  `/v3/api-docs` today.
- **Custom Prometheus counters** (e.g. index-rebuild count, availability-cache hit rate,
  DLT message count) — standard HTTP/JVM/Kafka-consumer metrics are exposed now via Micrometer;
  business-specific counters are a follow-up, not a correctness gap.
- **`backend/shared-libs` adoption** — unchanged from the bootstrap decision above.

## Package Structure

Hexagonal architecture — see
[`docs/services/search-service/domain-model.md`](../../../docs/services/search-service/domain-model.md)
and [`boundaries.md`](../../../docs/services/search-service/boundaries.md) for the full
rationale. Every layer is implemented, organized by feature within each layer:

- `domain/` — the `SearchableTrip` projection (with its staleness/terminal-state invariants),
  value objects, ports, `SearchRankingPolicy`, exceptions (framework-free)
- `application/usecase/{search,detail,suggestion,indexing,rebuild,availability}` —
  inbound-port implementations plus the shared `AvailabilityOverlay` collaborator
- `adapter/out/persistence` — JPA entity/mapper/specifications/adapter for the one repository port
- `adapter/out/client` — the `inventory-service` HTTP client (`RestClient`, degrade-on-failure)
- `adapter/out/cache` — Redis-backed availability cache (short-TTL, degrade-on-failure)
- `adapter/out/kafka` — the `ConsumerSeekAware`-based index-replay trigger
- `adapter/in/event` — Kafka listeners for `trip-events`/`review-events`
- `adapter/in/rest/{search,detail,suggestion,admin}` — controllers and DTOs, plus `exception/`
  (global mapping) and `filter/` (correlation id)
- `config/` — Jackson, CORS, OpenAPI, Redis, Kafka consumer factories + error handling, the
  inventory-service REST client, operational properties, use-case wiring
