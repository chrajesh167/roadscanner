# Payment Service — Responsibilities

## Responsibilities

- **Payment lifecycle & state machine** — the full `Payment` state machine
  (`payment-state-machine.md`), owned exclusively by this service. No other service mutates a
  payment's state, ever. This is the payment-side analogue of `booking-service` owning the booking
  state machine exclusively.
- **Payment initiation** — creating a `Payment` for a `PENDING_PAYMENT` booking and starting the
  transaction with the resolved payment gateway (`docs/architecture/payment-flow.md`'s "Initiation";
  FR-4.1). Synchronous confirmation for methods that confirm immediately, asynchronous
  (webhook-driven) for methods that don't.
- **Payment attempts & retries** — a failed payment can be retried as a **new `PaymentAttempt`
  against the same `Payment`** (and therefore the same pending booking), never a new booking, as
  long as the booking's seat hold has not expired (`docs/architecture/payment-flow.md`'s "Failure").
- **Gateway integration** — every interaction with an external payment gateway, isolated behind the
  `PaymentGateway` outbound port and a per-gateway adapter, so no other service and no domain code
  ever touches a gateway-specific type (`domain-model.md`'s "Payment Gateway Abstraction").
- **Webhook processing** — receiving, **verifying the signature of**, de-duplicating, and applying
  gateway webhooks; treating a repeated webhook for the same payment as a no-op, never a double
  charge or a duplicate `PaymentCompleted` (`docs/architecture/payment-flow.md`'s "Idempotency").
- **Payment idempotency** — client-supplied idempotency keys on payment initiation, and
  gateway-event de-duplication on webhooks, so a retried request or a redelivered webhook never
  produces a second charge or a second event (`domain-model.md`'s "Idempotency Strategy").
- **Refund execution** — executing refunds `booking-service` requests, tracking each refund's own
  lifecycle (`REFUND_PENDING → REFUNDED`, or a routed-to-support `RefundFailed`), and emitting the
  refund events (`docs/architecture/payment-flow.md`'s "Refund Handling"; FR-4.2).
- **Internal transaction ledger** — an insert-only record of every money movement (capture, refund)
  with its gateway references, the *"internal ledger of transactions"*
  `docs/architecture/service-boundaries.md` assigns to this service. This is the data a future
  accounting/settlement service would consume — it is **not** itself settlement.
- **Payment history & audit** — the immutable, timestamped record of every state transition on a
  `Payment` or `Refund`, and an insert-only `PaymentAudit` trail for security-sensitive events
  (webhook received, signature verified/rejected, refund initiated) — backing FR-8.3's support
  lookup and NFR-13's audit expectations.
- **Payment reconciliation** — periodically reconciling this service's ledger against the gateway's
  own records to detect discrepancies (a captured payment the gateway shows as failed, a late
  webhook that never arrived), and against `booking-service`'s cancellations
  (`events-consumed.md`), surfacing discrepancies for support rather than silently correcting money.
- **Publishing payment outcomes** — `PaymentCreated`, `PaymentCompleted`, `PaymentFailed`,
  `PaymentTimedOut`, `RefundInitiated`, `RefundCompleted`, `RefundFailed`
  (`events-published.md`), so `booking-service` can drive the booking state machine and
  `notification-service`/`analytics-service` can react.
- **Health, metrics, OpenAPI exposure** — non-negotiable per `.claude/ARCHITECTURE_RULES.md` and
  NFR-15, from the first deployable commit, same as every other service.

## Non-Responsibilities

- **Booking records, passenger details, booking state, ticket generation.** Entirely
  `booking-service`'s. `payment-service` holds only an opaque `BookingReference` to correlate a
  payment with a booking; it never reads or mutates booking state, and it never confirms, cancels,
  or completes a booking — it only *tells* `booking-service` (via events) what happened to the
  money, and `booking-service` decides what that means for the booking
  (`docs/architecture/booking-flow.md` steps 4–7).
- **The decision of whether a refund is owed, and the refund amount.** `booking-service` decides
  *whether* to refund and computes *how much* (from `operator-service`'s cancellation policy for
  traveler-initiated cancellations, or a full refund for trip cancellations —
  `docs/services/booking-service/use-cases.md`'s "Cancel Booking"). `payment-service` **executes**
  the refund it is handed, for the amount it is handed
  (`docs/architecture/service-boundaries.md`: *"it doesn't decide cancellation policy"*).
- **Cancellation-policy configuration.** `operator-service`'s, once it exists — `payment-service`
  never sees it and has no dependency on `operator-service` at all (`boundaries.md`).
- **Seat holds, seat reservations, live seat state, provider confirmation.**
  `provider-integration-service`'s and `booking-service`'s. `payment-service` has no concept of a
  seat and never calls `provider-integration-service` — the TTL relationship between a hold and
  payment-processing time is entirely `booking-service`'s and `provider-integration-service`'s
  concern (`docs/architecture/seat-locking-flow.md`).
- **Storing raw card / bank / payment-instrument data.** **Never** — NFR-12 (*"RoadScanner never
  stores raw payment card data; payment processing is delegated to a PCI-DSS-compliant payment
  gateway"*). This service stores only gateway references and status
  (`docs/architecture/high-level-design.md` §8), keeping RoadScanner out of PCI cardholder-data
  scope. See `data-ownership.md`.
- **Authentication / identity / credential issuance.** `auth-service`'s. `payment-service`
  independently validates the JWT signature/claims at its own boundary for authorization
  (`boundaries.md`'s "Payment ↔ Auth"), but never issues, stores, or looks up credentials, and
  never calls `auth-service` synchronously (`docs/architecture/authentication-flow.md`'s
  "Service-to-Service Calls").
- **Notification delivery.** `notification-service` reacts to `RefundCompleted`/`RefundFailed`
  (and, indirectly via `booking-service`'s `BookingConfirmed`, to payment success) on its own;
  `payment-service` never sends an email/SMS/push and never decides when a traveler is notified
  (`docs/architecture/service-boundaries.md`'s `notification-service` entry).
- **Fraud scoring / risk decisions.** A future `fraud-service`'s concern (`boundaries.md`).
  `payment-service` does not implement fraud rules; if such a service is built, it consumes payment
  events rather than being embedded here.
- **Operator settlement / payouts (FR-5.6).** A future accounting concern. `payment-service`'s
  ledger is the *input* a settlement process would read; computing operator payouts, commissions,
  and settlement cycles is out of scope (`boundaries.md`'s "Relationship to a Future Accounting
  Service").
- **Search, trip discovery, catalog.** `search-service`'s and `inventory-service`'s — no
  relationship exists (`boundaries.md`).

## Design Rationale for the Split

`payment-service` exists for the same reason `provider-integration-service` does: to concentrate a
**volatile, compliance-sensitive external integration** behind one canonical port, so its one reason
to change (a new gateway, a new payment method, a PCI requirement, a gateway API revision) is
isolated from every other service's reasons to change. Folding payment-gateway integration into
`booking-service` would drag PCI scope, gateway SDKs, webhook endpoints, and gateway-specific retry
semantics into the orchestrator — making `booking-service` responsible for a second job with a
different change cadence and a different compliance boundary, exactly the anti-pattern
`docs/architecture/service-boundaries.md` draws every boundary to avoid.

**Trade-off accepted:** payment now costs a client hop to a separate service and a cross-service
event round-trip back to `booking-service`, rather than a single in-process step. Accepted for the
same reason the platform accepts it everywhere else — the alternative re-couples an independently
compliance-scoped concern into a service that would then have to be PCI-reasoned-about as a whole
(`docs/architecture/high-level-design.md` §8, NFR-12).
