# Payment Service — Use Cases

Five kinds of use case: **client-facing** (via `api-gateway`), **service-to-service** (a direct
call from another backend service, no gateway), **webhook** (the external gateway calling in),
**event-driven** (Kafka), and **scheduled/operational**. See `domain-model.md` for the shapes these
operate on, `payment-state-machine.md` for the transitions they drive, and `sequence-diagrams.md`
for the full call sequences.

## Client-Facing (via `api-gateway`)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Initiate Payment** | Traveler pays for a `PENDING_PAYMENT` booking (FR-4.1; `docs/architecture/booking-flow.md` step 3) | Validate the request and the client `Idempotency-Key`; if a `Payment` already exists for that key, return it (no second gateway transaction). Otherwise create a `Payment` (`CREATED`) for the `bookingReference` and `amount`, resolve the `GatewayType` from configuration, start the gateway transaction via the `PaymentGateway` port, transition to `PENDING` (async methods) or drive straight toward `CAPTURED` (sync methods), publish `PaymentCreated` | The client calls this service **directly**, not through `booking-service` — a frozen platform decision (`boundaries.md`'s Channel 1). Amount and currency come from the request (quoted by `booking-service` from its frozen fare); `payment-service` never re-derives them. `TRAVELER`, must own the booking's payer identity |
| **Get Payment** | Traveler/support views a payment | Return the `Payment` after the ownership check in `boundaries.md`'s "Payment ↔ Auth" | Unauthorized access returns "not found," not "forbidden" |
| **Get Payment Status** | Client polls an in-flight payment (`docs/architecture/api-inventory.md`'s "Payment Status") | Return the current `PaymentStatus` (and, for a settled payment, its outcome) for a payment the caller owns | Supports the async (UPI/webhook) case where the client is waiting on an outcome that arrives out-of-band; `TRAVELER` (own) |

### Initiate Payment — the Idempotency and Single-Payment Rules

`Initiate Payment` enforces two invariants from `domain-model.md`: **at most one non-terminal
`Payment` per `bookingReference`**, and **client-idempotency-key de-duplication**. A retry after a
declined attempt does **not** create a second `Payment` — it adds a new `PaymentAttempt` to the
existing one (`docs/architecture/payment-flow.md`'s "Failure": *"a retry is a new payment attempt
against the same pending booking, not a new booking"*). A network-blip retry carrying the same
idempotency key returns the existing `Payment` unchanged. Together these are the payment-side
guarantee behind FR-4.3 ("a failed or partial payment must never leave a booking in an inconsistent
state").

## Service-to-Service (No Gateway)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Initiate Refund** | `booking-service` cancels a refund-eligible booking (`docs/architecture/payment-flow.md`'s "Refund Handling"; FR-4.2) | Given a payment reference, a **refund amount** (computed by `booking-service`), a reason, and an idempotency key: if a `Refund` already exists for `(paymentId, idempotencyKey)`, return it (no second refund). Otherwise create a `Refund` (`REQUESTED`), transition the `Payment` to `REFUND_PENDING` (full refund) or leave it `CAPTURED` (partial), send the refund to the gateway, publish `RefundInitiated` | **This is the authoritative refund trigger** — see `boundaries.md`'s "the Refund Trigger, Reconciled." `payment-service` executes the amount it is handed; it never computes a cancellation-fee policy. Consumed by `booking-service` (service-to-service) and `admin-console` (support overrides), per `docs/architecture/api-inventory.md` |
| **Get Refund** | `booking-service` / support checks a refund's progress | Return the `Refund` and its `RefundStatus` | `booking-service` requests a refund and moves on; it does not track the refund's own lifecycle in its state machine (`docs/services/booking-service/booking-state-machine.md`'s "Future Refund — Deliberately Not a State"), but may query it, as may support for FR-8.3 |

## Webhook (External Gateway → `payment-service`)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Handle Gateway Webhook** | A payment gateway posts an event (payment authorized/captured/failed, refund completed/failed) | **Verify signature** (per-gateway secret), **replay-check** (`gatewayEventId` + timestamp), **correlate** to a `Payment`/`Refund` by gateway references, then apply the corresponding transition idempotently; persist a `WebhookEvent` and a `PaymentAuditRecord` | The public, JWT-less endpoint (`boundaries.md`, `api-summary.md`). At-least-once, same as Kafka (`docs/architecture/payment-flow.md`'s "Idempotency"): a repeated webhook for the same `gatewayEventId` is a **no-op**, never a double charge or a duplicate `PaymentCompleted`. An unverifiable or replayed webhook is audited and rejected, never applied |

### The Transitions a Webhook Can Drive

- **Authorization confirmed** → `PENDING → AUTHORIZED` (auth-then-capture methods; no external
  event).
- **Capture confirmed** → `AUTHORIZED/PENDING → CAPTURED`, write a `CAPTURE` ledger line, publish
  `PaymentCompleted`.
- **Payment failed/declined** → `PENDING/AUTHORIZED → FAILED`, publish `PaymentFailed`.
- **Refund completed** → `Refund REQUESTED/PROCESSING → COMPLETED`, `Payment → REFUNDED` (full
  refund), write a `REFUND` ledger line, publish `RefundCompleted`.
- **Refund failed** → `Refund → FAILED`, publish `RefundFailed` (routed to support —
  `docs/architecture/payment-flow.md`).

### The Late-Success-After-Timeout Edge Case (Required Reconciliation Path)

`docs/architecture/payment-flow.md`'s explicit edge case: a capture webhook can arrive **after** a
timeout sweep already moved the `Payment` to `EXPIRED` (and `booking-service` already cancelled the
booking, seat possibly resold). `payment-service` **still records the payment as captured for
financial accuracy** — *"the money did move"* — writing the ledger line and emitting
`PaymentCompleted`. It does **not** silently keep the money: because the booking is already
cancelled, `booking-service`'s "Handle Payment Completed" (`docs/services/booking-service/use-cases.md`)
turns this into an **automatic refund plus a support-visible flag**. `payment-service`'s part is to
honor the money movement truthfully and emit the event; the refund is then requested back through
`Initiate Refund` like any other. This is *"a required reconciliation path, not a rare exception to
hand-wave"* (`docs/architecture/payment-flow.md`). See `sequence-diagrams.md`'s "Late Success After
Timeout."

## Event-Driven (Kafka)

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Reconcile Cancelled Booking** | `BookingCancelled` (from `booking-service`) | For a cancellation whose reason implies a refund is owed and whose `Payment` was captured: verify a corresponding `Refund` already exists (initiated via `Initiate Refund`). If none exists after a grace window, raise a `ReconciliationRecord` for support — **never** initiate a blind refund of an amount this service cannot compute | **Reconciliation only, not a refund initiator** — the resolution to the discovered conflict in `boundaries.md`'s "the Refund Trigger, Reconciled." Keyed by `bookingReference`/`paymentReference` + the refund idempotency key, so it can never double-refund. `docs/architecture/event-catalog.md` lists `payment-service` as a `BookingCancelled` consumer "only when a refund is owed" — this use case is exactly that consumption |

`payment-service` deliberately does **not** consume `BookingCreated`, `PaymentCompleted` (its own),
`TripCancelled`, or any seat/catalog event — see `events-consumed.md` for the full "does not consume"
list and why.

## Scheduled / Operational

| Use Case | Trigger | Behavior Summary | Notes |
|---|---|---|---|
| **Sweep Expired Payments** | Scheduled, low-frequency | For every `Payment` in `CREATED`/`PENDING`/`AUTHORIZED` past its `expiresAt` with no gateway outcome, transition to `EXPIRED` and publish `PaymentTimedOut` | `docs/architecture/payment-flow.md`'s "Timeout": *"payment-service cannot assume success or failure — it marks the payment `TIMED_OUT` once the window elapses."* The distinct `EXPIRED`/`PaymentTimedOut` (vs. `FAILED`/`PaymentFailed`) is preserved for reconciliation, because a late gateway confirmation is still physically possible (the edge case above) |
| **Reconcile Against Gateway** | Scheduled | Compare this service's ledger against the gateway's own records for a window; record matches and discrepancies as `ReconciliationRecord`s (a payment the gateway shows captured but RoadScanner shows expired, a refund the gateway shows failed, ...); surface discrepancies for support | Never silently moves money — reconciliation *detects*, humans *decide*, matching `docs/architecture/payment-flow.md`'s "a stuck refund is a customer-trust issue that needs a human" philosophy applied to all money discrepancies |
| **Sweep Stale Webhook Records** *(hygiene only)* | Scheduled, long interval | Prune `WebhookEvent` rows well past any replay window | The one genuinely transient category; never prunes `Payment`, `Refund`, ledger, or audit rows (`data-ownership.md`'s "Retention") |

## What's Deliberately Not a Use Case Here

- **Deciding whether a refund is owed, or computing its amount** — `booking-service`/
  `operator-service`'s (`responsibilities.md`). This service only *executes* a refund it is handed.
- **Confirming, cancelling, or completing a booking** — `booking-service`'s. This service emits
  payment/refund outcomes; it never touches booking state.
- **Holding, confirming, or releasing a seat** — `provider-integration-service`'s /
  `booking-service`'s. This service has no concept of a seat.
- **Sending any notification** — `notification-service` reacts to the refund events this service
  publishes; `payment-service` never sends an email/SMS/push (`boundaries.md`).
- **Fraud scoring or a fraud decision** — a future `fraud-service`'s (`boundaries.md`); not embedded
  here.
- **Operator settlement / payout computation (FR-5.6)** — a future accounting service's; this
  service only produces the ledger such a service would read.
