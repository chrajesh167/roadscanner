# Payment Service — API Summary

Category-level per `docs/architecture/api-inventory.md`'s convention (its `payment-service` row:
Payment Initiation, Payment Status, Refund), expanded here with concrete conceptual paths since —
like `booking-service`'s API summary — this is new surface with nothing external depending on it
yet. These paths are this specification's proposal for what becomes frozen once implementation
begins, not a contract another service's code already calls. `docs/architecture/api-inventory.md`'s
frozen category set is respected exactly; no endpoint is invented that violates it.

## Client-Facing (via `api-gateway`), under `/api/v1/payments`

All operations require an authenticated identity (NFR-11) — see `boundaries.md`'s "Payment ↔ Auth"
for the authorization rules layered on top.

| Operation | Conceptual Endpoint | Purpose | Requires |
|---|---|---|---|
| Initiate Payment | `POST /api/v1/payments` (with an `Idempotency-Key` header) | Start payment for a `PENDING_PAYMENT` booking (`api-inventory.md`'s "Payment Initiation") | `TRAVELER`, must be the booking's payer |
| Get Payment Status | `GET /api/v1/payments/{paymentId}/status` | Check the status of an in-flight payment (`api-inventory.md`'s "Payment Status") | `TRAVELER` (own) |
| Get Payment | `GET /api/v1/payments/{paymentId}` | Retrieve one payment | `TRAVELER` (own), `ADMIN`/`SUPPORT` (any) |

`Initiate Payment` is the one client-facing write, and it is the frozen exception to
`booking-service`'s single-orchestrator rule — the client calls `payment-service` **directly**, not
through `booking-service` (`docs/architecture/booking-flow.md` step 3;
`docs/services/booking-service/boundaries.md`'s "The one place the client legitimately bypasses
booking-service"). The `Idempotency-Key` header is mandatory and is the client-idempotency surface
from `domain-model.md`'s "Idempotency Strategy."

## Internal (Service-to-Service, No Gateway), under `/internal/api/v1/payments`

| Operation | Conceptual Endpoint | Purpose | Consumed By |
|---|---|---|---|
| Initiate Refund | `POST /internal/api/v1/payments/{paymentId}/refunds` (with an idempotency key) | Execute a refund of a `booking-service`-computed amount (`api-inventory.md`'s "Refund") | `booking-service` (service-to-service), `admin-console` (support overrides) |
| Get Refund | `GET /internal/api/v1/payments/{paymentId}/refunds/{refundId}` | Track a refund's progress | `booking-service`, `admin-console`/support (FR-8.3) |

This is the **authoritative refund trigger** (`boundaries.md`'s "the Refund Trigger, Reconciled"),
consumed by `booking-service` exactly as `docs/architecture/api-inventory.md` froze it (*"Refund |
... booking-service (service-to-service), admin-console (support overrides)"*). It matches the
established `/internal/api/v1/...` convention `provider-integration-service`, `inventory-service`,
and `booking-service` already use for service-to-service surface, and carries the same disclosed
Phase-1 posture on authentication: no per-request auth is implemented in the endpoint itself yet — it
relies on the platform's private-network boundary, the same gap
`docs/services/booking-service/api-summary.md` and `docs/services/provider-integration-service/`
already carry, expected to close once `api-gateway` enforces that `/internal/**` is never routed
publicly.

**Admin/support refund overrides use the same idempotent operation**, not a separate privileged
money-movement path (`boundaries.md`'s "Payment ↔ Auth") — support resolves an FR-8.3 case by
triggering the same refund a booking-driven cancellation would.

## Public Webhook (External Gateway → `payment-service`)

| Operation | Conceptual Endpoint | Purpose | Called By |
|---|---|---|---|
| Handle Gateway Webhook | `POST /webhooks/{gatewayType}` | Receive a gateway event (authorized/captured/failed, refund completed/failed) | The external payment gateway |

**This is the one public, JWT-less endpoint on the platform's payment surface**, and it is secured
differently from every other endpoint — see `boundaries.md`'s "Payment ↔ Auth and the Public Webhook
Boundary" and `domain-model.md`'s "Webhook Verification":

- **No JWT** — the gateway has no RoadScanner token. Authentication is by **gateway signature
  verification** against that gateway's signing secret, per `gatewayType`.
- **Replay protection** — `gatewayEventId` de-duplication (unique `WebhookEvent`) plus a timestamp
  tolerance window; a replayed or stale event is a no-op.
- **Idempotent** — a redelivered webhook for an already-processed event is a `200 OK` no-op, never a
  double charge or duplicate `PaymentCompleted` (`docs/architecture/payment-flow.md`'s "Idempotency").
- **Audited** — every webhook (verified or rejected) writes a `WebhookEvent` and a
  `PaymentAuditRecord` (NFR-13, FR-8.3).
- **Routing** — `api-gateway` must route `/webhooks/**` to `payment-service` **without** JWT
  enforcement, and must **never** route `/internal/**` publicly (an `api-gateway` decision, not
  designed here since that service doesn't exist yet).

The `{gatewayType}` path segment lets one endpoint serve every gateway while resolving the correct
adapter/verifier via the `PaymentGatewayRegistry` (`domain-model.md`) — the same
`/{providerType}`-scoped pattern `provider-integration-service`'s internal API uses.

## Error Responses

Follows either the RFC 7807 `ProblemDetail` convention or the platform's custom `ErrorResponse`
shape — this specification does not fix which, the same implementation-time call
`booking-service`'s `api-summary.md` leaves open (`inventory-service` uses `ProblemDetail`;
`auth-service`/`search-service`/`provider-integration-service` use a custom `ErrorResponse`). Either
way: Jakarta Validation for request-shape errors, a stable correlation-id-bearing error body for
every non-2xx (NFR-16), and **never a raw gateway response, stack trace, or any instrument-adjacent
detail surfaced to a client** (NFR-12). A declined payment returns a coarse, gateway-agnostic
failure classification, never the gateway's raw decline reason if that could leak instrument detail.

## What Callers Should Expect on Failure

| Failure | This service's behavior | Rationale |
|---|---|---|
| Gateway unreachable during `Initiate Payment` | Bounded retry then a clear, retryable error; no `Payment` left in a charged-but-untracked state | `domain-model.md`'s retry strategy; FR-4.3 |
| Duplicate `Idempotency-Key` on `Initiate Payment` | Return the existing `Payment` (no second gateway transaction) | Idempotency invariant (`domain-model.md`) |
| Gateway declines | `Payment → FAILED`, `PaymentFailed` emitted, `200`-with-outcome to the client is **not** used — a clear failure status | `booking-service` must see a real failure to cancel the booking |
| `Initiate Refund` retried by `booking-service` | Return the existing `Refund` (idempotent) | Refund idempotency invariant; prevents double refunds |
| Webhook signature invalid | Reject + audit; never applied | Webhook verification (`domain-model.md`) |

None of these degrade to a misleading `200`-with-sentinel — every one is a clear status, matching the
"correctness over availability" priority NFR-7 states for the booking/payment path.

## What's Deliberately Not Here

Concrete request/response JSON shapes, the `Idempotency-Key` header's exact format, pagination for
any list surface, the per-gateway signing-secret storage, the exact `api-gateway` routing table, and
the outbox-backed publish mechanism's internals (not client-facing). These are OpenAPI-contract and
implementation decisions made once implementation begins, matching
`docs/architecture/api-inventory.md`'s own stated scope and every other service's `api-summary.md`.
