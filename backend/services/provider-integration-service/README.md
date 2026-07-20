# Provider Integration Service

The platform's sole gateway to external transportation providers — FlixBus first, with RedBus,
AbhiBus, KSRTC, IntrCity, and future providers addable without touching any use case, port, or
existing adapter. See
[`docs/services/provider-integration-service/overview.md`](../../../docs/services/provider-integration-service/overview.md)
and the rest of that directory for the full design.

**Status: feature-complete for Phase 1.** All layers are implemented: domain, application use
cases, persistence (Postgres/Flyway), the Redis-backed session/capability/seat-map caches, the
Kafka audit publisher, Resilience4j-protected FlixBus HTTP integration, a complete in-memory Mock
provider, and the REST surface with OpenAPI documentation. See "API Surface" and "Remaining
Integration Points" below.

## API Surface

All internal-only, under `/internal/api/v1/providers/{providerType}` (see `/swagger-ui.html` for
the full contract):

| Endpoint | Purpose |
|---|---|
| `POST /sessions` | Authenticate against a provider, opening a session |
| `POST /sessions/{sessionId}/refresh` | Refresh a session's token before it expires |
| `GET /sessions/{sessionId}/trips` | Search the provider for trips (`origin`, `destination`, `date`) |
| `GET /sessions/{sessionId}/trips/{providerTripId}/seat-map` | Retrieve a trip's seat layout |
| `POST /sessions/{sessionId}/trips/{providerTripId}/seat-blocks` | Block one or more seats |
| `DELETE /sessions/{sessionId}/seat-blocks/{providerBlockReference}` | Release a seat block (idempotent) |
| `POST /sessions/{sessionId}/seat-blocks/{providerBlockReference}/booking` | Confirm a booking against a block |
| `GET /sessions/{sessionId}/bookings/{bookingReference}/ticket` | Download the ticket (base64 content) |
| `GET /capabilities` | Capability discovery — no session required |
| `GET /health` | On-demand health probe — no session required |

This service implements no authentication or authorization itself, per this project's explicit
scope — `api-gateway` is the platform's authentication boundary
(`docs/architecture/authentication-flow.md`), and is expected to never route `/internal/**`
publicly. `booking-service`, `search-service`, and `inventory-service` are the only intended
callers (see "Future Design Rules" in the original request) — no other RoadScanner service, and
no client, is meant to reach this API directly.

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

The app starts on `:8083` with the `local` profile. Useful local endpoints once running:
- `http://localhost:8083/actuator/health`
- `http://localhost:8083/swagger-ui.html`

Walk the full Mock-provider journey end to end (no FlixBus credentials needed — `MOCK` ships
`enabled=true`):

```bash
SESSION=$(curl -s -X POST http://localhost:8083/internal/api/v1/providers/MOCK/sessions | jq -r .sessionId)

curl -s "http://localhost:8083/internal/api/v1/providers/MOCK/sessions/$SESSION/trips?origin=Mumbai&destination=Pune&date=2026-08-01" | jq .

TRIP=$(curl -s "http://localhost:8083/internal/api/v1/providers/MOCK/sessions/$SESSION/trips?origin=Mumbai&destination=Pune&date=2026-08-01" | jq -r '.trips[0].providerTripId')

SEAT=$(curl -s "http://localhost:8083/internal/api/v1/providers/MOCK/sessions/$SESSION/trips/$TRIP/seat-map" | jq -r '.seats[] | select(.status=="AVAILABLE") | .seatNumber' | head -1)

BLOCK=$(curl -s -X POST "http://localhost:8083/internal/api/v1/providers/MOCK/sessions/$SESSION/trips/$TRIP/seat-blocks" \
  -H 'Content-Type: application/json' -d "{\"seatNumbers\":[\"$SEAT\"]}" | jq -r .providerBlockReference)

curl -s -X POST "http://localhost:8083/internal/api/v1/providers/MOCK/sessions/$SESSION/seat-blocks/$BLOCK/booking" \
  -H 'Content-Type: application/json' \
  -d "{\"providerTripId\":\"$TRIP\",\"passengers\":[{\"fullName\":\"Jane Doe\",\"age\":30,\"gender\":\"F\",\"seatNumber\":\"$SEAT\"}]}" | jq .
```

### Running Fully Containerized

```bash
docker build -t roadscanner/provider-integration-service .
docker run --network host -e SPRING_PROFILES_ACTIVE=local -p 8083:8083 roadscanner/provider-integration-service
```

(`docker-compose.yml` intentionally does not include `provider-integration-service` itself — same
rationale as `auth-service`/`search-service`'s identical omission.)

## Building & Testing

```bash
./mvnw compile   # compiles cleanly
./mvnw test       # requires Docker running (Testcontainers — Postgres + Redis + Kafka)
./mvnw package    # builds the executable jar
```

`./mvnw test` runs the full suite: framework-free unit tests for the domain layer (`ProviderSession`/
`SeatReservation`/`ProviderHealth`'s idempotent state transitions, `ProviderClientRegistry`'s
resolution rules), application-layer tests against in-memory fakes and a fully-configurable
`StubProviderClient`, a complete `MockProviderClientAdapter` behavioral suite (search → seat map →
block → confirm → ticket, release idempotency, every failure mode), `MockRestServiceServer` tests
for the FlixBus HTTP clients (request shape, response mapping, HTTP-status-to-exception
translation), Testcontainers-backed integration tests for every persistence adapter and both
Redis cache adapters, a real-Kafka publish/consume round-trip for the audit publisher, a
resilience test proving the `flixbus` circuit breaker actually opens under sustained failure,
`@WebMvcTest` controller tests, and `ProviderIntegrationServiceEndToEndTest` driving the full
Mock-provider journey over real HTTP.

*(In the environment this service was built in, Docker was unavailable, so the 39 framework-free
unit/application tests plus the `MockRestServiceServer` and `@WebMvcTest` suites — 53 tests total,
no Docker required — were run and pass; the Testcontainers-backed suites listed above compile
cleanly but have not been executed in that environment. They follow the exact same
`TestcontainersConfiguration`/`@ServiceConnection` pattern `auth-service` and `search-service`
already use successfully in CI, so they're expected to pass under normal Docker availability —
run `./mvnw test` locally to confirm.)*

Docker Desktop is the assumed container runtime — Testcontainers and `docker compose` both work
with it out of the box, no extra environment configuration needed.

## Configuration Profiles

| Profile | Use | Datasource / Redis / Kafka / FlixBus |
|---|---|---|
| `local` | Running on a developer machine | `localhost`, matching `docker-compose.yml`; FlixBus base URL/credentials are placeholder values (see "Remaining Integration Points") |
| `dev` | Deployed dev environment | Every value from an env var, no hardcoded fallback for secrets |
| `test` | Test execution | Only non-infrastructure properties; datasource/Redis/Kafka come from Testcontainers via `@ServiceConnection` |

No profile is active by default — a missing profile should fail loudly, matching
`auth-service`/`search-service`'s convention.

## Architectural Decisions

**Provider extensibility = an open `ProviderType` value object plus a runtime registry, not a
closed `enum`.** `ProviderType` wraps a normalized code string; `ProviderClientRegistry`
(`domain.service`) resolves it to whichever `ProviderClient` bean declares that type via
`supportedType()`. Spring collects every `ProviderClient` `@Component` into the list the registry
is built from. See "How to Add a New Provider" below for the concrete steps this enables.

**`ProviderClient` is one outbound port, implemented via composition for FlixBus.**
`FlixBusProviderClientAdapter` implements the port by delegating to five focused collaborators
(`FlixBusAuthenticationClient`, `FlixBusSearchClient`, `FlixBusSeatClient`,
`FlixBusBookingClient`, `FlixBusTicketClient`), each a thin `RestClient` wrapper, plus
`FlixBusMapper` (wire-contract translation) and `FlixBusExceptionTranslator` (HTTP/timeout →
canonical exception hierarchy). `MockProviderClientAdapter` implements the same port directly,
backed by `MockProviderDataStore` (in-memory, thread-safe, deterministic per search query).

**No real FlixBus API access exists.** `FlixBus*Client` is implemented and tested against a
self-consistent request/response contract documented in `FlixBusMapper`'s Javadoc — RoadScanner
has no real FlixBus B2B API documentation or credentials. Every field, endpoint, and error-status
mapping is invented but internally consistent, fully covered by `MockRestServiceServer` tests, and
entirely swappable via `FlixBusProperties` (`roadscanner.provider.flixbus.*`) the moment a real
contract is available.

**Only `ProviderUnavailableException` trips the FlixBus circuit breaker or gets retried.** A
seat-unavailable or booking-declined response is a legitimate business outcome, not a
provider-health signal — retrying or circuit-breaking it would be wrong. `resilience4j.circuitbreaker.instances.flixbus.record-exceptions`
and `resilience4j.retry.instances.flixbus.retry-exceptions` are both scoped to that one exception
type; every `FlixBus*Client`'s `fallbackMethod` re-throws any other
`ProviderIntegrationException` subtype unchanged (see `FlixBusExceptionTranslator#translateFallback`'s
Javadoc) and only wraps genuine infrastructure failures (open circuit, bulkhead/rate-limiter
rejection, an unexpected exception) into `ProviderUnavailableException`.

**Provider health uses an active scheduled poller as the primary signal, not just reactive
circuit-breaker state.** `ProviderHealthMonitor` calls `CheckProviderHealth` for every enabled
provider every 30s (`ProviderMaintenanceScheduler`); `CheckProviderHealthService` records the
result and publishes `ProviderUnavailable`/`ProviderRecovered` only on the two transitions that
matter (into `UNAVAILABLE` from anything else; back to `HEALTHY` specifically from
`UNAVAILABLE` — see that class's Javadoc for why a first-ever healthy check is not a "recovery").
The same use case backs the on-demand `GET /health` endpoint, so the logic exists in exactly one
place regardless of trigger.

**Sessions are the only state this service persists that flows through every operation; seat
reservations, bookings, and tickets are not persisted at all.** Per this service's explicit
non-responsibilities (it does not own booking state or inventory), `SeatReservation`/
`BookingConfirmation`/`ProviderTicket` are pass-through, canonically-mapped responses returned
once and forgotten — the calling service (`inventory-service`/`booking-service`) is expected to
track whatever correlation IDs it needs on its own side.

**Tokens are stored as plain columns, not hashed.** Unlike `auth-service`'s refresh tokens (looked
up by presented value, so a one-way hash works), this service must send the access/refresh token
back to the provider on every call — see `V2__create_provider_sessions.sql`'s top-of-file comment
for the field-level-encryption follow-up this implies for real production use.

**No `backend/shared-libs` dependency yet.** Same reasoning and same deviation as `auth-service`/
`search-service`'s pom.xml — those modules are still empty placeholder directories.

**No Lombok, `ddl-auto: validate` never `update`.** Same conventions as every other service in
this codebase.

## Remaining Integration Points

Implemented but awaiting other platform components or a deliberate operational decision (tracked,
not forgotten):

- **No real FlixBus base URL or credentials.** The `FLIXBUS` row in `provider_configurations` is
  seeded `enabled=false`; flipping it on is a config change (`FLIXBUS_BASE_URL`,
  `FLIXBUS_CLIENT_ID`, `FLIXBUS_CLIENT_SECRET` env vars in the `dev` profile), not a code change.
- **`booking-service`, `search-service`, and `inventory-service` don't exist yet**, so nothing
  calls this service in a real environment today — every endpoint is implemented and tested
  against the Mock provider and hand-issued requests.
- **Token columns are not encrypted at rest** — see "Architectural Decisions" above.
- **The internal REST surface has no authentication or network-level restriction** — expected to
  come from `api-gateway` never routing `/internal/**` publicly; not wired up yet since
  `api-gateway` doesn't exist either (same disclosed gap `search-service`'s README already has for
  its own `/internal/search/reindex`).
- **Testcontainers-backed test suites were not executed in the environment this service was
  built in** (Docker was unavailable there) — they compile cleanly and follow the same pattern
  already proven by `auth-service`/`search-service`'s CI runs; see "Building & Testing" above.
- **Custom Prometheus counters** (block success/failure rate, per-provider latency, circuit
  breaker trip count) — standard HTTP/JVM/Resilience4j metrics are exposed now via Micrometer;
  business-specific counters are a follow-up, not a correctness gap.
- **`backend/shared-libs` adoption** — unchanged from the bootstrap decision above.

## How to Add a New Provider (e.g. RedBus)

This is the concrete payoff of the `ProviderClientRegistry` design — no existing code changes:

1. Create `adapter/out/provider/redbus/` mirroring the FlixBus package: a `RedBusProviderClientAdapter`
   implementing the `ProviderClient` port (`supportedType()` returns `new ProviderType("REDBUS")`),
   whatever HTTP client collaborators RedBus's real API shape calls for, a mapper, an exception
   translator, and a `@ConfigurationProperties` class for its base URL/credentials.
2. Add a Flyway migration (`V6__seed_redbus_provider_configuration.sql`) inserting its
   `provider_configurations` row — `enabled=false` until credentials are configured, matching the
   `FLIXBUS` pattern.
3. Add a `resilience4j.circuitbreaker.instances.redbus` / `resilience4j.retry.instances.redbus`
   block to `application.yml`, scoped to `ProviderUnavailableException` exactly like `flixbus`'s.
4. That's it. `ProviderClientRegistry` picks up the new `@Component` bean automatically; every
   inbound port, application use case, REST controller, and test already works against any
   `ProviderType` with a registered adapter — none of them import `RedBus*` at all.

## Package Structure

Hexagonal architecture — see
[`docs/services/provider-integration-service/domain-model.md`](../../../docs/services/provider-integration-service/domain-model.md)
and [`boundaries.md`](../../../docs/services/provider-integration-service/boundaries.md) for the
full rationale.

- `domain/` — models (`Provider`, `ProviderSession`, `SeatReservation`, `ProviderHealth`, ...),
  ports (`in`/`out`), the `ProviderClientRegistry` domain service, exceptions (framework-free)
- `application/usecase/{auth,search,seatmap,seatblock,booking,ticket,capability,health,session,audit}` —
  inbound-port implementations plus shared collaborators (`ActiveSessionResolver`, `AuditRecorder`,
  `SessionExpirySweeper`, `ProviderHealthMonitor`)
- `adapter/out/provider/{flixbus,mock}` — the two provider integrations, fully isolated per provider
- `adapter/out/persistence` — JPA entity/mapper/repo/adapter per aggregate (`ProviderConfiguration`,
  `ProviderSession`, `AuditRecord`, `ProviderHealth`)
- `adapter/out/cache` — Redis-backed `TokenCache`/`ProviderCache` adapters
- `adapter/out/kafka` — the audit event publisher
- `adapter/in/rest/{session,search,seatmap,seatblock,booking,ticket,capability,health}` —
  controllers and DTOs, plus `exception/` (global mapping) and `filter/` (correlation id)
- `config/` — Jackson, CORS, OpenAPI, Redis, persistence, Kafka producer, operational properties,
  use-case wiring, the two scheduled maintenance jobs
