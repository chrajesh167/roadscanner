# Booking Service — API Summary

Category-level per `docs/architecture/api-inventory.md`'s convention
(`docs/architecture/api-inventory.md`'s own `booking-service` row: Booking Creation, Booking
Lifecycle Management, Ticket Retrieval), expanded here with concrete conceptual paths since —
unlike `inventory-service`'s or `provider-integration-service`'s API summaries, which document an
*already-shipped* contract — this is new surface with nothing external depending on it yet. These
paths are this specification's proposal for what becomes frozen once implementation begins, not
a contract another service's code already calls.

## Client-Facing (via `api-gateway`), under `/api/v1/bookings`

All operations require an authenticated identity (NFR-11) — see `boundaries.md`'s "Booking ↔
Auth" for the authorization rules layered on top of authentication.

| Operation | Conceptual Endpoint | Purpose | Requires |
|---|---|---|---|
| Get Seat Selection View | `GET /api/v1/inventory/trips/{tripId}/seats` *(served by `booking-service`, not `inventory-service`, despite the path prefix matching that service's resource — an implementation/routing decision, not fixed here)* | Composed static layout + live status | `TRAVELER` |
| Hold Seats | `POST /api/v1/bookings/holds` | Place a temporary hold on selected seat(s) for a trip | `TRAVELER` |
| Release Hold | `DELETE /api/v1/bookings/holds/{holdReference}` | Voluntarily abandon a hold before booking | `TRAVELER`, must own the hold |
| Create Booking | `POST /api/v1/bookings` | Create a `PENDING_PAYMENT` booking against a held reference, with passenger details | `TRAVELER`, must own the hold |
| Get Booking | `GET /api/v1/bookings/{bookingId}` | Retrieve one booking | `TRAVELER` (own), `OPERATOR` (own trip), `ADMIN`/`SUPPORT` |
| List Booking History | `GET /api/v1/bookings` | Every booking for the requesting traveler, all statuses | `TRAVELER` |
| List Trip Bookings | `GET /api/v1/bookings?tripId=` | Every booking against a trip the requesting operator owns | `OPERATOR`, ownership check depends on `operator-service` (see `boundaries.md`) |
| Cancel Booking | `POST /api/v1/bookings/{bookingId}/cancel` | Cancel a booking, per `use-cases.md`'s "Cancel Booking" | `TRAVELER` (own), `ADMIN`/`SUPPORT` |
| Get Ticket | `GET /api/v1/bookings/{bookingId}/ticket` | Download the confirmed e-ticket | `TRAVELER` (own), `CONFIRMED`/`COMPLETED` only |

**Note on the seat-selection path prefix:** it is listed under `/api/v1/inventory/...` above
because that is the resource the traveler conceptually views (a trip's seats), matching how a
client would naturally navigate from `inventory-service`'s own trip-detail response. Whether this
is actually routed to `booking-service` at the `api-gateway` level under that path, or exposed
under `/api/v1/bookings/trips/{tripId}/seats` instead, is a routing decision left to
implementation — flagged explicitly so it isn't mistaken for an accidental collision with
`inventory-service`'s own `GET /api/v1/inventory/trips/{tripId}/seat-layout`
(`docs/services/inventory-service/api-summary.md`), which remains that service's own, unchanged,
static-shape-only endpoint.

## Internal (Service-to-Service, No Gateway)

| Operation | Conceptual Endpoint | Purpose | Consumed By |
|---|---|---|---|
| Verify Booking | `GET /internal/api/v1/bookings/verify?travelerId=&tripId=` | Does a verified, `COMPLETED` booking exist for this traveler/trip pair — backs FR-7.2 | `review-service` (not yet built) |

Matching `provider-integration-service`'s and `inventory-service`'s established
`/internal/api/v1/...` convention for service-to-service-only surface, and their identical,
disclosed posture on authentication: no authentication is implemented in this endpoint itself in
Phase 1 — it relies on the platform's private network boundary, the same gap
`docs/services/provider-integration-service/README.md` and `docs/services/search-service/README.md`
already carry for their own `/internal/**` surfaces, expected to close once `api-gateway` enforces
that `/internal/**` is never routed publicly.

## Error Responses

Follows the same RFC 7807 `ProblemDetail` convention `inventory-service` adopted (a deliberate,
disclosed deviation from the platform's more common custom `ErrorResponse` record used by
`auth-service`, `search-service`, and `provider-integration-service`) **or** the platform's custom
`ErrorResponse` shape — this specification does not fix which, since `booking-service`'s own
consistency need (matching `inventory-service`'s newer convention vs. matching the majority of
already-shipped services) is an implementation-time call, not an architecture one. Either way:
Jakarta Validation for request-shape errors, a stable correlation-id-bearing error body for every
non-2xx response, and never a raw exception or stack trace surfaced to a client.

## What Callers Should Expect on Failure (Composed-Call Semantics)

Because most client-facing operations here compose calls to `inventory-service` and
`provider-integration-service`, the error a client sees is not always this service's own:

| Upstream failure | This service's behavior | Rationale |
|---|---|---|
| `inventory-service` unreachable or trip not found | Fail the operation with a clear, retryable error | NFR-7 — never proceed with a hold or booking against a trip that can't be verified (`boundaries.md`) |
| `provider-integration-service` reports the seat unavailable | Fail `Hold Seats` with that specific outcome | The provider's own accept/reject is authoritative (`docs/architecture/seat-locking-flow.md`) |
| `provider-integration-service` unreachable | Fail the operation; no local fallback lock exists to reach for | Matches `docs/architecture/seat-locking-flow.md`'s stated failure mode for a third-party-provider relay |
| Trip has no `ProviderMapping` | Fail `Hold Seats` with "cannot currently be booked" | `overview.md`'s ambiguity #2 |

None of these degrade to a `200`-with-sentinel response — every one is a clear error status,
matching the "correctness over availability" priority NFR-7 states for this specific path
(explicitly the opposite of `inventory-service`'s own "degrade, not fail" posture toward
`search-service`'s availability queries, which are display-only and never correctness-critical).

## What's Deliberately Not Here

Concrete request/response JSON shapes, pagination conventions for "List Booking History," the
exact `api-gateway` routing table, and the outbox-backed publish mechanism's own internal
surface (there is none — it's not client-facing). These are OpenAPI-contract and implementation
decisions made once implementation of this service begins, matching
`docs/architecture/api-inventory.md`'s own stated scope.
