# Payment Service — Domain Model

## Why This Model Looks the Way It Does

`payment-service` protects two real, high-stakes invariants — a payment's state transitions must be
correct and exclusive to this service, and the platform must **never end up charged-without-a-
confirmed-booking or confirmed-without-payment** (FR-4.3, NFR-7) — and otherwise holds only what it
needs to talk to a gateway and correlate the result back to a booking. Like
`booking-service`'s model, it holds a small amount of data by volume but is the *only* source of
truth for that data: no other service tracks a payment's status, a refund's lifecycle, or the
gateway references behind them.

Two structural decisions are worth stating up front, both mirroring patterns already frozen
elsewhere on the platform:

1. **`Payment` and `Refund` are two separate aggregates**, not one — exactly as
   `booking-service` splits `SeatHold` from `Booking`. A `Payment` can have zero refunds (the
   common case), or more than one over its life (a partial refund followed by another). A refund has
   its own attempts, its own gateway references, and its own success/failure lifecycle that must be
   trackable independently of the payment that spawned it. Folding refunds into `Payment` would
   force `Payment` to carry a second, differently-shaped lifecycle inside itself.

2. **`GatewayType` is an open value object, not a closed `enum`** — copied deliberately from
   `provider-integration-service`'s `ProviderType` (`docs/services/provider-integration-service/domain-model.md`:
   *"an open value object (normalized code string), not a closed enum — the entire mechanism behind
   'add a provider without changing business logic'"*). This is the single most important
   extensibility decision in the model: adding Stripe or Adyen after Razorpay is a new adapter
   package plus a configuration row, never a change to the domain.

## Core Identifiers

All UUID-wrapping value objects, matching every other identifier on this platform (`BookingId`,
`TripId`, `ProviderSessionId`, ...). Assigned by `payment-service`, never by a client.

- **`PaymentId`** — one payment intent for one booking.
- **`PaymentAttemptId`** — one attempt to pay a `Payment`.
- **`RefundId`** — one refund against a `Payment`.
- **`RefundAttemptId`** — one attempt to execute a `Refund`.
- **`TransactionId`** — one ledger entry.

## Value Objects

- **`Money`** — `amount` (`BigDecimal`, minor-unit-safe) + `currency` (ISO 4217). Currency is
  **never hardcoded to one market** (NFR-22) — every amount carries its own currency, the same
  posture `provider-integration-service`'s `FareAmount` already takes. A `Payment`'s amount is
  immutable once created.
- **`BookingReference`** — an opaque pointer to a `booking-service` `Booking`. `payment-service`
  never dereferences it against `booking-service`'s database (that would violate
  `docs/architecture/database-ownership.md`) — it uses it only to key events and to correlate
  webhooks and refund requests to the right payment. This is the exact mirror of
  `booking-service`'s own `paymentReference` (`docs/services/booking-service/domain-model.md`):
  each service holds an opaque reference into the other's data, neither reads the other's tables.
- **`GatewayType`** — the open value object above (e.g. `"RAZORPAY"`, `"STRIPE"`, `"ADYEN"`),
  resolved at runtime by the `PaymentGatewayRegistry` to a concrete adapter.
- **`PaymentMethod`** — a **closed** `enum` (`CARD`, `UPI`, `WALLET`, `NETBANKING`, ...), the
  platform-defined vocabulary of methods (FR-4.1). Closed for the same reason
  `provider-integration-service`'s `ProviderCapability` is closed: this vocabulary is
  platform-defined and grows only when *this service's* feature set does, never when a gateway is
  added. Whether a given gateway supports a given method is the adapter's concern, not the domain's.
- **`GatewayReference`** — the set of opaque identifiers a gateway hands back (`gatewayPaymentId`,
  `gatewayOrderId`, `gatewayRefundId`, ...). Stored as strings; **never** contains card/instrument
  data (NFR-12).
- **`IdempotencyKey`** — a client-supplied key on payment initiation, and the gateway's own event id
  on webhooks; both are the basis of the idempotency strategy below.
- **`PaymentStatus`** / **`RefundStatus`** — the state enums below.
- **`GatewayWebhookSignature`** / **`GatewayEventId`** — carriers used during webhook verification.

## `PaymentStatus` (enum) — the Richer Internal Vocabulary

Per `overview.md`'s Conflict 3, this service models the richer states the brief asks for, mapped
onto `docs/architecture/payment-flow.md`'s frozen coarse statuses (full mapping in
`payment-state-machine.md`):

```
CREATED → PENDING → AUTHORIZED → CAPTURED
CREATED / PENDING / AUTHORIZED → FAILED
CREATED / PENDING → CANCELLED
CREATED / PENDING → EXPIRED
CAPTURED → REFUND_PENDING → REFUNDED
```

| Internal state | Meaning | Frozen `payment-flow.md` equivalent |
|---|---|---|
| `CREATED` | Payment intent persisted; gateway transaction not yet started | `INITIATED` |
| `PENDING` | Gateway transaction started; awaiting an async outcome (webhook) | `INITIATED` (in-flight) |
| `AUTHORIZED` | Gateway authorized the funds but has not captured them yet (auth-then-capture methods) | *(internal only — no external event)* |
| `CAPTURED` | Money has moved; the payment succeeded | `COMPLETED` |
| `FAILED` | Gateway declined or errored | `FAILED` |
| `CANCELLED` | Payment voided/abandoned before capture (traveler cancelled, or an authorization was voided) | *(surfaces to booking as `PaymentFailed` — see below)* |
| `EXPIRED` | No gateway outcome arrived within the acceptable window | `TIMED_OUT` |
| `REFUND_PENDING` | A full refund has been initiated against a captured payment | `REFUND_INITIATED` |
| `REFUNDED` | The full refund completed | `REFUND_COMPLETED` |

**`CANCELLED` and `EXPIRED` both surface to `booking-service` as frozen events**, so
`booking-service`'s frozen state machine is untouched: `EXPIRED` emits `PaymentTimedOut`,
`CANCELLED` emits `PaymentFailed` (a payment voided before capture must cause the booking to cancel
and its seat to release, which is exactly `booking-service`'s `PaymentFailed` handling —
`docs/services/booking-service/use-cases.md`'s "Handle Payment Failed"). `AUTHORIZED` emits no
external event — it is an internal waypoint on the road to `CAPTURED`, invisible to the rest of the
platform. See `payment-state-machine.md` for every transition and its emitted event.

## `RefundStatus` (enum)

```
REQUESTED → PROCESSING → COMPLETED
REQUESTED / PROCESSING → FAILED
```

| Refund state | Meaning | Frozen `payment-flow.md` equivalent | Event |
|---|---|---|---|
| `REQUESTED` | `booking-service` asked for a refund; persisted, idempotency-checked | `REFUND_INITIATED` | `RefundInitiated` |
| `PROCESSING` | The refund request has been sent to the gateway; awaiting confirmation | `REFUND_INITIATED` (in-flight) | — |
| `COMPLETED` | The gateway confirmed the refund | `REFUND_COMPLETED` | `RefundCompleted` |
| `FAILED` | The gateway refund attempt failed | `REFUND_FAILED` | `RefundFailed` (routed to support) |

A `Payment`'s own `status` moves to `REFUND_PENDING`/`REFUNDED` **only for a full refund**; a
partial refund leaves `Payment.status = CAPTURED` with an associated `Refund` aggregate carrying the
detail. This keeps the brief's requested `REFUND_PENDING`/`REFUNDED` payment states meaningful
(full refund) without overloading them for the partial case. See `payment-state-machine.md`.

## Aggregates & Entities

### `Payment` (aggregate root)

The one thing this service exists to own:

- `id` (`PaymentId`)
- `bookingReference` (`BookingReference`) — the booking this payment is for; **exactly one**
  non-terminal `Payment` may exist per booking reference at a time (invariant below)
- `travelerId` — the payer; every authorization check in `boundaries.md`'s "Payment ↔ Auth"
  resolves against this
- `amount` (`Money`) — immutable after creation; quoted by `booking-service` from the fare it froze
  at hold time (`docs/services/booking-service/data-ownership.md`), so a payment's amount is the
  amount the traveler committed to, never re-derived from the catalog
- `method` (`PaymentMethod`)
- `gatewayType` (`GatewayType`) — which gateway is processing this payment
- `gatewayReference` (`GatewayReference`) — populated as the gateway hands back identifiers;
  contains no instrument data (NFR-12)
- `status` (`PaymentStatus`) — the state machine above; owned exclusively here
- `idempotencyKey` (`IdempotencyKey`) — the client key that created this payment; unique
- `attempts` — `List<PaymentAttempt>` (entity, below)
- `expiresAt` — the deadline past which, with no gateway outcome, the payment is swept to `EXPIRED`
  (see "Retry & Timeout Strategy")
- `createdAt`, `authorizedAt`, `capturedAt`, `failedAt`, `cancelledAt`, `expiredAt` — each `null`
  until its transition occurs; together the audit trail this specification's "payment history"
  responsibility refers to
- `version` — optimistic-locking column (see "Concurrency")

**Deliberately not held:** any card/bank/instrument data of any kind (NFR-12 — the gateway holds
it, RoadScanner never does), anything about the booking beyond the opaque reference (that is
`booking-service`'s, per `docs/architecture/database-ownership.md`), and any cancellation-policy or
refund-eligibility rule (that is `booking-service`/`operator-service`'s — this service is handed a
refund *amount*, never a policy).

### `PaymentAttempt` (entity, within the `Payment` aggregate)

Each attempt to pay the `Payment` through the gateway. A retry after a failure is a **new
`PaymentAttempt` on the same `Payment`**, per `docs/architecture/payment-flow.md`'s "Failure"
scenario (*"a retry is a new payment attempt against the same pending booking, not a new
booking"*):

- `id` (`PaymentAttemptId`)
- `attemptNumber`
- `gatewayReference` — the gateway identifiers for this specific attempt
- `outcome` (`SUCCEEDED` / `FAILED` / `TIMED_OUT` / `IN_PROGRESS`)
- `failureCode` / `failureReason` — the canonical, gateway-agnostic failure classification
  translated at the adapter boundary (see "Exception & Error Translation")
- `startedAt`, `settledAt`

### `PaymentTransaction` (entity — the internal ledger, insert-only)

One immutable ledger line per actual money movement — a capture or a refund — with its gateway
references. This is the *"internal ledger of transactions"*
`docs/architecture/service-boundaries.md` assigns to this service. Insert-only, never updated or
deleted (the same insert-only posture `provider-integration-service`'s `AuditRecord` takes):

- `id` (`TransactionId`)
- `paymentId` / `refundId` (whichever this line records)
- `type` (`CAPTURE` / `REFUND`)
- `amount` (`Money`)
- `gatewayReference`
- `occurredAt`

This is the data a future accounting/settlement service would consume (`boundaries.md`); it is not
itself settlement.

### `Refund` (aggregate root)

A refund against a captured `Payment`:

- `id` (`RefundId`)
- `paymentId` — the payment being refunded
- `bookingReference` — carried through so refund events can be keyed and correlated the same way
  payment events are
- `amount` (`Money`) — **supplied by `booking-service`**, never computed here
  (`responsibilities.md`'s non-responsibilities)
- `reason` — a coarse, gateway-agnostic reason echoed from `booking-service`'s cancellation
  (traveler-requested, trip-cancelled, provider-confirmation-failed, late-success-after-timeout)
- `status` (`RefundStatus`)
- `idempotencyKey` — so a `booking-service` retry of the same refund request never creates a second
  refund (invariant below)
- `gatewayReference`
- `attempts` — `List<RefundAttempt>`
- `createdAt`, `completedAt`, `failedAt`
- `version` — optimistic lock

### `RefundAttempt` (entity, within the `Refund` aggregate)

Each attempt to execute the refund through the gateway — same shape rationale as `PaymentAttempt`.
Crucially, a `RefundFailed` does **not** trigger unbounded automatic retries: per
`docs/architecture/payment-flow.md` (*"a failed refund is not silently retried forever...
`RefundFailed` routes to support"*), a failed refund is surfaced for manual intervention, not
looped.

### `WebhookEvent` (aggregate, insert-only)

One persisted record per inbound gateway webhook, the backbone of idempotency and replay protection:

- `id`
- `gatewayType`
- `gatewayEventId` — the gateway's own unique event id; **unique constraint** — a second webhook
  carrying the same `gatewayEventId` is a no-op (`docs/architecture/payment-flow.md`'s "Idempotency")
- `signatureVerified` (boolean)
- `receivedAt`, `payloadDigest` (a hash of the raw payload for audit — not the raw card-bearing
  payload itself, per NFR-12)
- `processingOutcome`

Insert-only; the same "durable record of an external interaction" role
`provider-integration-service`'s `AuditRecord` plays for provider events.

### `PaymentAuditRecord` (aggregate, insert-only)

One entry per security-sensitive event — webhook received, signature verified/rejected, refund
initiated, payment captured — backing FR-8.3 and NFR-13. Insert-only, never mutated.

### `ReconciliationRecord` (aggregate, insert-only)

The result of a reconciliation run: matched vs. discrepant payments/refunds against the gateway's
own records, and against `booking-service`'s cancellations (`events-consumed.md`). Discrepancies are
recorded for support, never used to silently move money.

## Payment Gateway Abstraction (the SOLID / Hexagonal Core)

Structurally identical to `provider-integration-service`'s provider abstraction, because the
problem is identical — one service wrapping N volatile external integrations behind one canonical
port:

- **`PaymentGateway` (outbound port)** — the domain-facing interface the application layer depends
  on. Conceptual operations: `createTransaction`, `authorize`, `capture`, `void`, `refund`,
  `verifyWebhookSignature`, `parseWebhook`. Every method speaks only in this service's own value
  objects (`Money`, `GatewayReference`, `PaymentMethod`, ...) — **never** a gateway-specific type.
- **`PaymentGatewayRegistry`** — resolves a `GatewayType` to the concrete adapter at runtime, the
  direct analogue of `provider-integration-service`'s `ProviderClientRegistry`. This is "the entire
  mechanism behind adding a gateway without changing business logic."
- **Per-gateway adapters** (`adapter/out/gateway/razorpay`, `.../stripe`, `.../adyen`) — the **only**
  packages in the platform that import a gateway's SDK/types. Each contains its own mapper
  (`RazorpayMapper`, ...) and its own exception translator (below). Adding a gateway is a new
  adapter package plus a configuration row (a `Gateway` seed, mirroring
  `provider-integration-service`'s `Provider` Flyway seed) — no domain or application change.

**The domain layer never knows which gateway is used.** This is a hard rule (the brief states it
explicitly, and it matches `docs/services/provider-integration-service/boundaries.md`'s enforced
"no service imports FlixBus types outside the adapter" rule). Which gateway processes a payment is
resolved from configuration (per market, per method, or per booking), not decided in domain code.

### Architecture Test (Recommended — Documentation Only, Not Yet Implemented)

Gateway/domain isolation is, like `provider-integration-service`'s equivalent rule, enforced by
package-structure and code-review discipline — it is not something the compiler or Kafka can
technically prevent. To make the rule **cheap to keep** rather than merely stated, this
specification **recommends an automated architecture test** (for example, ArchUnit, run in the
service's normal test suite) asserting the following, once implementation begins. This is a
documentation recommendation only — no test is written in this pass:

- **The domain layer does not import Spring.** No type under the domain package
  (`domain..`) may depend on `org.springframework..` — the domain is a plain-Java, framework-free
  core (Clean/Hexagonal Architecture, `docs/architecture/high-level-design.md` §1).
- **The domain layer does not import any gateway SDK.** No type under `domain..` (nor the
  application layer) may depend on a gateway vendor package (`com.razorpay..`, `com.stripe..`,
  `com.adyen..`, or any future gateway SDK).
- **Only gateway adapters may depend on gateway implementations.** Only packages under
  `adapter.out.gateway..` may import a gateway SDK; the `PaymentGateway` port,
  `PaymentGatewayRegistry`, and everything they are called from remain gateway-agnostic.
- **No gateway-specific type leaks outside its adapter package.** A type defined under
  `adapter.out.gateway.razorpay..` (etc.) must not be referenced from any other package — only the
  canonical domain value objects (`Money`, `GatewayReference`, `PaymentMethod`, `GatewayType`, ...)
  and the canonical exception hierarchy (`PaymentIntegrationException` and its subtypes) cross the
  adapter boundary.

The intent mirrors the ArchUnit-style rule already recommended for
`provider-integration-service`'s FlixBus isolation: turn a governance rule into a build-time
assertion so a leak fails CI instead of surviving to review. See `boundaries.md`'s "What's
Deliberately Out of Scope" for the pointer back to this recommendation.

## Exception & Error Translation

Every gateway-specific failure — an HTTP status, a timeout, a declined card, a malformed webhook —
is translated at the adapter boundary into a canonical, gateway-agnostic exception hierarchy,
exactly as `provider-integration-service` does (`docs/services/provider-integration-service/domain-model.md`'s
"Exception Hierarchy"). Conceptual types: `PaymentDeclinedException`, `PaymentAuthorizationException`,
`GatewayUnavailableException`, `WebhookVerificationException`, `RefundFailedException`,
`PaymentNotFoundException`, all extending `PaymentIntegrationException`, which carries a canonical
`PaymentError` (code, message, **retryability**). Application and REST-layer code never sees a
gateway-specific exception type. Retryability drives the retry strategy below.

## Invariants

- **At most one non-terminal `Payment` per `BookingReference`.** A booking cannot have two live
  payments racing to capture. A retry reuses the existing `Payment` (new `PaymentAttempt`); it never
  creates a second `Payment`. This is the core FR-4.3 guarantee ("a failed or partial payment must
  never leave a booking in an inconsistent state") expressed as a uniqueness rule.
- **`amount` and `currency` are immutable** once a `Payment` is created — a payment is a commitment
  to a specific amount, the same "frozen at commit time" posture `booking-service` takes toward
  fare (`docs/services/booking-service/data-ownership.md`).
- **`status` only moves forward** through the state machine (`payment-state-machine.md`); terminal
  states (`FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED`) are never left. `CAPTURED` is terminal for
  the forward path but may enter the refund sub-lifecycle.
- **A captured payment reaches `CAPTURED` exactly once**, and emits `PaymentCompleted` exactly once
  — even if the gateway delivers the success webhook more than once (webhook idempotency below).
- **A `Refund` is created at most once per `(paymentId, idempotencyKey)`** — a `booking-service`
  retry of the same refund request is a no-op returning the existing `Refund`, never a second
  refund. This is what makes the refund path safe against `booking-service`'s at-least-once retries
  and against the `BookingCancelled` reconciliation consumer (`boundaries.md`).
- **Total refunded amount never exceeds the captured amount** across all `Refund`s for a `Payment`.
- **Every state transition is idempotent** — a duplicate webhook, a redelivered event, or a retried
  request must be a no-op the second time, matching `docs/architecture/event-catalog.md`'s
  platform-wide at-least-once model and `docs/architecture/payment-flow.md`'s explicit idempotency
  requirement. Enforced by checking current `status` before applying a transition, backed by the
  `version` optimistic lock for concurrent-trigger races.

## Idempotency Strategy

Two distinct idempotency surfaces, because there are two distinct at-least-once sources:

1. **Client-supplied idempotency key on payment initiation.** The client sends an `Idempotency-Key`
   with `Initiate Payment`; a repeat of the same key returns the existing `Payment` rather than
   creating a second one (and therefore never starts a second gateway transaction). This protects
   against a client retry after a network blip — the payment analogue of `booking-service`'s
   "a hold token becomes at most one booking."
2. **Gateway-event de-duplication on webhooks.** Every webhook carries the gateway's own
   `gatewayEventId`; the unique constraint on `WebhookEvent.gatewayEventId` makes a redelivered
   webhook a no-op. This is `docs/architecture/payment-flow.md`'s explicit rule: *"payment-service
   must treat a repeated notification for the same payment as a no-op — never a double charge or a
   duplicate `PaymentCompleted` event."*

The refund path has its own idempotency key (the invariant above), closing the third at-least-once
source (`booking-service` retrying a refund request, or the `BookingCancelled` reconciliation
consumer).

## Retry & Timeout Strategy

- **Gateway-call retries (transient).** A `PaymentError` marked retryable (a timeout, a 5xx) may be
  retried by the adapter under a **bounded** retry with backoff and a circuit breaker, the same
  resilience posture `provider-integration-service` applies to provider calls
  (`docs/architecture/high-level-design.md` §11). A non-retryable error (a decline) is never
  retried — it is a `FAILED` outcome.
- **Payment attempt retries (traveler-driven).** After a `FAILED` attempt, the traveler may retry;
  this is a new `PaymentAttempt` on the same `Payment`, valid only while the booking's seat hold
  has not expired (`docs/architecture/payment-flow.md`'s "Failure"). `payment-service` does not know
  the hold's TTL — it simply allows another attempt; `booking-service` is the one that will have
  already cancelled the booking (releasing the seat) if the hold expired, and the resulting
  `PaymentFailed`/late-success reconciliation covers the edge (`sequence-diagrams.md`).
- **Timeout / expiry.** If no gateway outcome (sync response or webhook) arrives by `Payment.expiresAt`,
  a scheduled sweep transitions the `Payment` to `EXPIRED` and emits `PaymentTimedOut`
  (`use-cases.md`'s "Sweep Expired Payments"). This is `payment-flow.md`'s "Timeout" scenario:
  *"payment-service cannot assume success or failure — it marks the payment `TIMED_OUT` once the
  window elapses."*
- **Refund retries.** A `RefundFailed` is **not** auto-retried indefinitely — it routes to support
  (`payment-flow.md`). A bounded, deliberate retry before giving up is an implementation decision;
  unbounded silent retry is explicitly forbidden.

## Webhook Verification

Every inbound webhook is verified before any state change, in this order:

1. **Signature verification** — the gateway adapter verifies the webhook's signature against that
   gateway's signing secret (HMAC or the gateway's own scheme), via `PaymentGateway.verifyWebhookSignature`.
   A failed verification is recorded (`WebhookEvent.signatureVerified = false`, a `PaymentAuditRecord`)
   and rejected — never applied.
2. **Replay protection** — the webhook's timestamp is checked against a tolerance window, and its
   `gatewayEventId` against the `WebhookEvent` store; a replayed or too-old event is a no-op.
3. **Correlation** — the webhook is matched to an existing `Payment`/`Refund` by its gateway
   references; a webhook for an unknown reference is recorded and flagged, never blindly applied.
4. **Apply** — only a verified, de-duplicated, correlated webhook drives a state transition.

See `boundaries.md`'s "Payment ↔ Auth and the Public Webhook Boundary" for why the webhook endpoint
is the one public, JWT-less endpoint on this service and how it is secured instead.

## Concurrency

**Optimistic locking (`version`) on `Payment` and `Refund`**, the same platform-wide pattern
`booking-service`'s `Booking`, `inventory-service`'s `Trip`, and `search-service`'s `SearchableTrip`
use. It guards the most plausible race: a webhook applying a `CAPTURED` transition at the same
moment a timeout sweep is applying `EXPIRED`, or two redelivered webhooks racing. Whichever write
loses the version check retries under its own handler's semantics (webhook redelivery, the sweep's
next run) and finds the transition already applied — a no-op, per the idempotency invariant. No
distributed lock is needed; there is no cross-service seat-style contention here — a payment is
owned by one booking and processed by one gateway.

## Soft Delete

**None.** No `Payment`, `Refund`, `PaymentTransaction`, or audit row is ever deleted — financial and
audit records must remain queryable indefinitely for support (FR-8.3), dispute resolution,
reconciliation, and compliance (NFR-13). Terminal statuses are terminal *states*, not deletions.
`WebhookEvent` rows may be pruned on a long retention schedule once well past any replay window, the
one genuinely transient category — but never the ledger or the payment records themselves. See
`data-ownership.md`.

## Aggregate Summary

| Concept | Kind | Authority | Kept Current By |
|---|---|---|---|
| `Payment` | Aggregate root | Owned outright — the platform's only source of truth for payment state | This service's own state machine |
| `PaymentAttempt` | Entity in `Payment` | Owned outright | Each gateway attempt |
| `PaymentTransaction` | Entity — internal ledger | Owned outright, insert-only | Each money movement |
| `Refund` / `RefundAttempt` | Aggregate / entity | Owned outright | `Initiate Refund` + gateway outcome |
| `WebhookEvent` | Aggregate, insert-only | Owned outright | Each inbound webhook |
| `PaymentAuditRecord`, `ReconciliationRecord` | Aggregate, insert-only | Owned outright | Audit / reconciliation runs |
| `BookingReference` | Opaque reference — **not owned** | `booking-service` | N/A — never dereferenced |
| Refund eligibility / amount policy | **Not modeled here at all** | `booking-service` / `operator-service` | N/A |
| Card / instrument data | **Never stored** (NFR-12) | The external gateway | N/A |
