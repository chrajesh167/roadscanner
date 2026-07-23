# Booking Service

RoadScanner's booking orchestration layer — the platform's only source of truth for booking
records, composing catalog facts from `inventory-service` with live provider actions from
`provider-integration-service` into the booking lifecycle. See
[`docs/services/booking-service/overview.md`](../../../docs/services/booking-service/overview.md)
and the rest of that directory — frozen after two architecture reviews — for the full design.

**Status: feature-complete for Phase 1.** Domain, application use cases, persistence
(Postgres/Flyway, optimistic locking), JWT-verified REST API (RFC 7807 errors, OpenAPI), Kafka
inbound (`inventory-service`'s `TripCancelled`, `provider-integration-service`'s audit topic,
`payment-service`'s payment events) and outbound (`booking-events`) adapters are all implemented.
Two upstream dependencies (`operator-service`, `payment-service`) don't exist yet — see
"Remaining Integration Points".

## API Surface

Client-facing, under `/api/v1/bookings` (JWT bearer auth required — see `/swagger-ui.html` for
the full contract):

| Endpoint | Purpose |
|---|---|
| `GET /trips/{tripId}/seats` | Seat-selection view — static layout + live status, composed |
| `POST /holds` | Hold seat(s) for a trip (`TRAVELER` only) |
| `DELETE /holds/{seatHoldId}` | Release a hold before booking (idempotent) |
| `POST /` | Create a `PENDING_PAYMENT` booking against a held reference |
| `GET /{bookingId}` | Get a booking — ownership-checked (404, not 403, when denied) |
| `GET /?tripId=` | List bookings for a trip (`OPERATOR`/`ADMIN`/`SUPPORT`) |
| `GET /?onBehalfOfTravelerId=` | List another traveler's history (`ADMIN`/`SUPPORT` only) |
| `GET /` | List the caller's own booking history |
| `POST /{bookingId}/cancel` | Cancel a booking (idempotent) |
| `GET /{bookingId}/ticket` | Download the confirmed e-ticket |

Internal, service-to-service only, under `/internal/api/v1/bookings` (no auth in Phase 1 — see
"Remaining Integration Points"):

| Endpoint | Purpose |
|---|---|
| `GET /verify?travelerId=&tripId=` | Does a verified, `COMPLETED` booking exist — backs FR-7.2, consumed by `review-service` |

## Requirements

- Java 21
- Docker (for local Postgres/Kafka via `docker-compose.yml` at the repo root, and for
  Testcontainers-backed integration tests). No Redis — this service uses none.

No local Maven install required — use the wrapper (`./mvnw`).

## Running Locally

```bash
# from the repo root: start this service's Postgres, plus the shared Kafka
docker compose up -d postgres-booking kafka

# from this directory: run the app natively against them
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The app starts on `:8085` with the `local` profile, expecting `inventory-service` (`:8084`) and
`provider-integration-service` (`:8083`) to already be running natively. It also expects a valid
JWT bearer token on every `/api/v1/bookings/**` request — see "JWT Verification in Local
Development" below for why this doesn't yet interoperate with a locally-running `auth-service`
out of the box.

### JWT Verification in Local Development

`roadscanner.security.jwt.ephemeral-keys: true` in `application-local.yml` generates a throwaway
RS256 key pair at startup, exactly matching `auth-service`'s own local-dev convenience. **This
does not let this service verify a real token from a separately-running `auth-service`
instance** — `auth-service`'s own `application-local.yml` also uses `ephemeral-keys: true`,
generating an unrelated key pair every restart, so the two services' keys never match today. To
test the full chain locally, configure both services with the same fixed PEM key pair via
`roadscanner.security.jwt.public-key-pem` (this service) and
`roadscanner.security.jwt.private-key-pem`/`public-key-pem` (`auth-service`) — see "Remaining
Integration Points".

### Running Fully Containerized

```bash
docker build -t roadscanner/booking-service .
docker run --network host -e SPRING_PROFILES_ACTIVE=local -p 8085:8085 roadscanner/booking-service
```

(`docker-compose.yml` intentionally does not include `booking-service` itself — same rationale as
every other service's identical omission.)

## Building & Testing

```bash
./mvnw compile   # compiles cleanly
./mvnw test       # requires Docker running (Testcontainers — Postgres + Kafka)
./mvnw package    # builds the executable jar
```

`./mvnw test` runs the full suite: framework-free domain unit tests (`Booking`'s full state
machine — every transition in `booking-state-machine.md`, including idempotency — plus
`SeatHold`), application-layer tests against in-memory fakes and fully-configurable stub clients
(covering the "one passenger per held seat" validation, ownership/role authorization for every
port, the provider-confirmation-failure and late-payment-success edge cases, and the trip-
cancellation cascade), Kafka-listener dispatch tests, Testcontainers-backed integration tests for
both persistence adapters, `@WebMvcTest` controller tests authenticated via Spring Security
Test's `SecurityMockMvcRequestPostProcessors.jwt()`, and `BookingServiceEndToEndTest` driving the
full hold → create → get → ticket flow over real HTTP, with `inventory-service`/
`provider-integration-service` stubbed via an embedded WireMock server.

*(In the environment this service was built in, Docker was unavailable, so the 90 tests requiring
no Docker — domain, application-layer, Kafka dispatch, and the `@WebMvcTest` suite — were run and
pass; the 17 Testcontainers-backed tests (2 persistence adapter classes, 1 end-to-end class)
compile cleanly and fail solely with `Previous attempts to find a Docker environment failed` — no
other error — confirming they are blocked only by the missing Docker daemon, not by any code
defect. They follow the exact same `TestcontainersConfiguration`/`@ServiceConnection` pattern
every other service in this codebase already uses successfully in CI, so they're expected to pass
under normal Docker availability — run `./mvnw test` locally to confirm.)*

## Configuration Profiles

| Profile | Use | Datasource / Kafka / JWT / upstream services |
|---|---|---|
| `local` | Running on a developer machine | `localhost`, matching `docker-compose.yml`; ephemeral JWT keys (see above); `inventory-service`/`provider-integration-service` base URLs point at `localhost` |
| `dev` | Deployed dev environment | Every value from an env var, no hardcoded fallback — including the real JWT public key matching `auth-service`'s signing key |
| `test` | Test execution | Only non-infrastructure properties; datasource/Kafka come from Testcontainers via `@ServiceConnection`; ephemeral JWT keys with a matching test-only signer (`testsupport.security.TestJwtIssuer`) |

No profile is active by default — a missing profile should fail loudly, matching every other
service's convention.

## Architectural Decisions

**Booking Service is the sole orchestrator of Seat Hold, Booking creation, and Provider
Confirmation — verified explicitly during the pre-implementation architecture review.** The
client never calls `inventory-service` or `provider-integration-service` directly, at any point,
in any flow. The one place the client legitimately reaches a different backend service is
`payment-service` (submitting payment) — a documented, frozen platform decision
(`docs/architecture/booking-flow.md` step 3), not client-side orchestration of the booking outcome.

**`Hold Seats` is a separate client-facing step from `Create Booking`, deliberately.** FR-3.2
requires the hold to exist "during checkout" — the span of time the traveler spends entering
passenger details — not just at the instant of submission. This is also why `SeatHold` is a
separate, short-lived aggregate from `Booking`: a hold that's abandoned (FR-3.4) never becomes a
`Booking` row nobody would recognize as a real booking attempt.

**`Create Booking` validates a hold via a local `expiresAt` comparison, never a fresh call to
`provider-integration-service`.** That service has no read-only "get reservation status"
operation — `BlockSeat`'s own response already returns the TTL this service needs, captured on
the `SeatHold` (and carried onto `Booking` as `holdExpiresAt`) at hold time. This resolves a real
gap between `docs/architecture/booking-flow.md`'s literal wording ("a read call") and
`provider-integration-service`'s actual, frozen port set.

**Post-confirmation cancellation is refund-only — no provider-side reversal is ever attempted.**
`provider-integration-service` has no operation for reversing an already-confirmed booking
(`ReleaseSeat` only covers a still-`BLOCKED` reservation). Adding one would mean redesigning that
service's frozen contract, which was explicitly out of scope for this implementation. This is a
real, documented product limitation, not an oversight — see
`docs/services/booking-service/boundaries.md`'s "Known Gap: Post-Confirmation Cancellation" for
the full reasoning and the recommended follow-up (a future `CancelBooking` port).

**Only trips with a `ProviderMapping` can be held or booked.** A first-party
(`operator-service`-sourced) trip with no provider equivalent has no `providerType`/
`providerTripId` to call `BlockSeat`/`ConfirmBooking` with — `Hold Seats` fails validation with a
clear "cannot currently be booked" outcome for such a trip. This is a real, currently-unclosed
platform gap (first-party trips are searchable but not yet bookable), not a `booking-service`
defect.

**`operator-service` and `payment-service` dependencies use documented, conservative interim
adapters.** `operator-service` doesn't exist to answer cancellation-policy questions —
`DefaultOperatorCancellationPolicyClient` defaults to full refund eligibility, no fee (the safest
choice for the traveler). `operator-service` also can't verify trip ownership for `OPERATOR`
requesters — `DefaultOperatorTripOwnershipVerifier` fails closed (always denies), so operators
cannot yet use `List Trip Bookings` until this exists. `payment-service` doesn't exist to receive
refund requests — `NoOpRefundRequestAdapter` logs what it would have requested and returns; a
booking's own state transition is never blocked on this. All three adapters are one-line swaps
once the real dependency exists — none of their ports' shapes need to change.

**JWT verification mirrors `auth-service`'s own RS256 design exactly, structurally incapable of
issuing tokens.** `adapter.out.security.JwtDecoderKeyMaterial` only ever holds a public key,
loaded from `roadscanner.security.jwt.public-key-pem` — there is no code path in this service
that can hold or use a private signing key in production, matching `auth-service`'s own security
rationale: *"compromising a downstream service only exposes its ability to verify, never to
issue."* Authorization (ownership, role checks) is decided entirely within this service's own
application layer, against its own data — never via URL-level `hasRole()` rules beyond the
platform-wide "must be authenticated at all."

**No Redis.** Nothing in the frozen architecture docs mandates one — `SeatHold` rows are already
short-lived Postgres rows with a TTL-like `expiresAt` field, not a cache in front of anything.

**No `backend/shared-libs` dependency yet; no Lombok; `ddl-auto: validate` never `update`.** Same
conventions as every other service in this codebase.

## Remaining Integration Points

Implemented but awaiting other platform components or a deliberate operational decision (tracked,
not forgotten):

- **`operator-service` doesn't exist yet** — cancellation-policy lookup and operator trip-
  ownership verification both use the conservative interim adapters described above. See
  `docs/services/booking-service/boundaries.md`'s "Relationship to `operator-service`".
- **`payment-service` doesn't exist yet** — the full `PENDING_PAYMENT → CONFIRMED` flow, and
  refund requests, are built against `payment-service`'s documented (not yet real) contract.
  `HandlePaymentCompletedServiceTest` exercises this path fully against fakes; no real end-to-end
  payment flow can run until `payment-service` ships its own producer.
- **`SeatReleased` is consumed by design but not yet published** by
  `provider-integration-service`'s current implementation
  (`docs/services/provider-integration-service/events-published.md`). `Sweep Stale Holds`
  (a scheduled job) is the interim safety net.
- **No real, shared JWT key pair between `auth-service` and `booking-service` in local
  development** — see "JWT Verification in Local Development" above. Both services' `dev`
  profiles already source the real key material from environment variables, so this is a
  local-dev-only gap, not a deployed-environment one.
- **The internal `/internal/api/v1/bookings/verify` endpoint has no authentication** — matches
  `inventory-service`'s and `provider-integration-service`'s identical, disclosed `/internal/**`
  gap (relies on the private network boundary until `api-gateway` enforces that path is never
  routed publicly).
- **Testcontainers-backed test suites were not executed in the environment this service was
  built in** (Docker was unavailable there) — see "Building & Testing" above.
- **Custom Prometheus counters** (booking funnel conversion, cancellation-reason breakdown,
  support-flagged-booking count) — standard HTTP/JVM/Kafka-consumer metrics are exposed now via
  Micrometer; business-specific counters are a follow-up, not a correctness gap.
- **The transactional outbox pattern named in `docs/architecture/high-level-design.md` §6** is
  not implemented — this service's own state transitions are internally consistent (single-
  service Postgres writes), but the Postgres-write-then-Kafka-publish boundary is not yet
  atomic. Flagged in `docs/services/booking-service/boundaries.md` as explicitly out of scope for
  the architecture specification; a real gap to close before the `booking-service` ↔
  `payment-service` path needs saga-grade consistency.
- **`backend/shared-libs` adoption** — unchanged from the bootstrap decision above.

## Package Structure

Hexagonal architecture — see
[`docs/services/booking-service/domain-model.md`](../../../docs/services/booking-service/domain-model.md)
and [`boundaries.md`](../../../docs/services/booking-service/boundaries.md) for the full
rationale.

- `domain/` — models (`Booking`, `SeatHold`, `Passenger`, `Ticket`, `RequesterContext`, ...),
  ports (`in`/`out`), exceptions (framework-free)
- `application/usecase/{hold,booking,cancellation,payment,trip,ticket,verification,scheduled}` —
  inbound-port implementations
- `adapter/in/event` — `TripCancelledListener`, `SeatReleasedListener`, `PaymentEventListener`
  and their wire messages
- `adapter/in/rest/{hold,booking,ticket,verification}` — controllers and DTOs, plus `exception/`
  (RFC 7807 global mapping) and `filter/` (correlation id)
- `adapter/out/persistence` — JPA entity/mapper/repo/adapter per aggregate
- `adapter/out/client` — `InventoryClientAdapter`, `ProviderIntegrationClientAdapter`, and the
  three interim adapters for not-yet-real dependencies
- `adapter/out/kafka` — the `booking-events` publisher
- `adapter/out/security` — JWT verification key material (`JwtDecoderKeyMaterial`,
  `EphemeralJwtKeyPair`)
- `config/` — Jackson, CORS, OpenAPI, persistence, Kafka, JWT/security, REST clients, use-case
  wiring, the two scheduled jobs
