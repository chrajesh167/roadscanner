# Payment Service — Overview

> **STATUS: FROZEN — READY FOR IMPLEMENTATION (2026-07-27).** The `payment-service` architecture has
> completed review and is frozen. No further redesign should be performed unless implementation
> exposes a genuine architectural defect. The final freeze pass made only three documentation
> adjustments: (1) `PaymentCreated` kept and documented as an explicitly informational event
> (below); (2) an ArchUnit architecture-test recommendation added for gateway/domain isolation
> (`domain-model.md`, `boundaries.md`); (3) a final internal-consistency audit. No architecture, no
> existing service contract, and no other service's documentation was changed.

> **New service specification, written against the frozen platform architecture as of 2026-07-27.**
> This document set is the implementation specification for `payment-service`. It is written the
> same way the frozen `booking-service` specification was — by reading every existing
> architecture and service document closely, integrating into their contracts *exactly* as
> written, and, where the task brief's requested vocabulary diverges from an already-frozen
> platform contract, **flagging the conflict and reconciling it explicitly rather than silently
> redesigning the platform** (`docs/services/booking-service/domain-model.md`'s "Reconciling the
> Requested State Vocabulary" set the precedent this document follows). Three such conflicts were
> found; all three are resolved below and again at their point of relevance, and **none required
> changing any existing service's frozen REST or Kafka contract.**

## Purpose

`payment-service` owns the **complete payment lifecycle** for RoadScanner — it is the platform's
single source of truth for all payment- and refund-related data. It is the only service that talks
to an external payment gateway, and it informs `booking-service` of every payment outcome
(`docs/architecture/high-level-design.md` §3: *"payment-service | Payment processing, refunds,
ledger | Owns: Payment transactions, refunds"*; `docs/architecture/service-boundaries.md`'s
`payment-service` entry: *"payment initiation, gateway integration, refunds, an internal ledger of
transactions"*).

It is to payment gateways exactly what `provider-integration-service` is to transportation
providers: the one place a volatile, compliance-sensitive, provider-specific external integration
is isolated behind a canonical, provider-agnostic port, so that no other service on the platform
ever imports a gateway-specific type or knows which gateway processed a given payment. This overview
leans on that analogy deliberately and often, because the platform already has a proven, frozen
model for "one service that wraps a volatile external integration" and `payment-service` is the
second instance of it.

## Bounded Context

**In — owned outright (`payment-service` is the sole source of truth):**

- **`Payment`** — the payment intent and lifecycle for one booking.
- **`PaymentAttempt`** — each attempt to pay a `Payment` through the gateway (a retry is a new
  attempt against the same `Payment`, per `docs/architecture/payment-flow.md`'s "Failure" scenario).
- **`PaymentTransaction`** — the internal ledger record of an actual money movement (capture,
  refund) with its gateway references.
- **`Refund`** / **`RefundAttempt`** — a refund against a captured `Payment`, and each gateway
  attempt to execute it.
- **`PaymentStatus`** / **`PaymentHistory`** — the payment state machine and the immutable audit
  trail of its transitions.
- **Webhook processing** — verifying, de-duplicating, and applying gateway webhooks.
- **Payment idempotency** — client-supplied idempotency keys and gateway-event de-duplication.
- **Payment gateway integration** — the `PaymentGateway` outbound port and its per-gateway adapters.
- **Payment audit** and **payment reconciliation** — insert-only audit records and the
  reconciliation of RoadScanner's ledger against the gateway's own records.

**Out — never owned here (each has an authoritative owner elsewhere):**

- **`Booking`, passenger details, booking state** — `booking-service`
  (`docs/services/booking-service/`). `payment-service` holds only an opaque `BookingReference`.
- **`Trip`, inventory, catalog, provider mappings** — `inventory-service`.
- **Seat holds, seat reservations, live seat state** — `provider-integration-service`.
- **The *decision* of whether a refund is owed, and the refund *amount*** —
  `booking-service`/`operator-service` policy (`docs/architecture/service-boundaries.md`:
  *"payment-service executes refunds it's told to execute, it doesn't decide cancellation
  policy"*). `payment-service` **executes** refunds; it never computes a cancellation-fee schedule.
- **Authentication / identity** — `auth-service`. `payment-service` validates JWTs at its own
  boundary but never issues or stores credentials.
- **Notification delivery** — `notification-service`, which consumes this service's refund events.
- **Search, provider APIs** — `search-service`, `provider-integration-service`.
- **Operator settlement / payouts (FR-5.6)** — a future accounting concern, not this service's
  ledger. See `boundaries.md`'s "Relationship to a Future Accounting Service."

## Where It Sits

- `docs/architecture/high-level-design.md` §3 (service inventory), §6 (booking↔payment is the one
  strongly-consistent, saga/outbox path on the platform), §8 (payment data never enters
  RoadScanner's own database beyond gateway references — NFR-12).
- `docs/architecture/payment-flow.md` — the frozen, cross-service payment/refund coordination this
  service implements the payment side of. **This is the single most authoritative document for this
  service; where the task brief and this document diverge, this document wins.**
- `docs/architecture/event-catalog.md`'s "Payment Events" and "Booking Lifecycle Events" — the
  frozen Kafka contract this service produces into and consumes from.
- `docs/architecture/api-inventory.md`'s `payment-service` row — the frozen public-API category set
  (Payment Initiation, Payment Status, Refund).
- `docs/architecture/service-boundaries.md`'s `payment-service` entry, and
  `docs/services/booking-service/` — the frozen consumer this service's events and refund API are
  built to satisfy without changing anything on the `booking-service` side.

## High-Level Flow (Summary)

```
Traveler
  │  (client calls payment-service directly — deliberate, see boundaries.md)
  ▼
payment-service ──► create Payment (CREATED) for a PENDING_PAYMENT booking
  │
  ▼
PaymentGateway adapter (Razorpay / Stripe / Adyen — resolved at runtime, domain never knows which)
  │
  ▼
Traveler completes payment at the gateway
  │
  ▼
Gateway Webhook ──► payment-service: verify signature, de-dupe, apply
  │
  ▼
payment-service: Payment → CAPTURED, publish PaymentCompleted
  │
  ▼
booking-service consumes PaymentCompleted ──► confirms booking with the provider
```

Failure, timeout, and refund variants are in `sequence-diagrams.md`; the full state machine is in
`payment-state-machine.md`. See `use-cases.md` for every inbound port and `boundaries.md` for every
service relationship reviewed one by one.

## Three Conflicts Found Between the Task Brief and the Frozen Platform, and How They Are Resolved

Writing this specification required reconciling the task brief's requested vocabulary against the
**already-frozen** `payment-flow.md`, `event-catalog.md`, and `booking-service` consumer contracts.
Three genuine conflicts surfaced. Per the brief's own "AMBIGUITIES" instruction (*"Stop. Explain
the conflict. Recommend the smallest possible change. Do NOT silently redesign the platform."*),
each is stated here and resolved with the least-invasive choice that keeps every existing frozen
contract literally true.

### Conflict 1 — Event name `PaymentSucceeded` (brief) vs. `PaymentCompleted` (frozen)

The brief asks `payment-service` to publish **`PaymentSucceeded`**. The frozen
`docs/architecture/event-catalog.md` and `docs/architecture/payment-flow.md` name this event
**`PaymentCompleted`**, and `docs/services/booking-service/events-consumed.md` is **already built to
consume `PaymentCompleted`** (*"`PaymentCompleted` | Trigger provider confirmation and transition to
`CONFIRMED`"*).

**Resolution: adopt the frozen name `PaymentCompleted`.** Renaming it to `PaymentSucceeded` would
mean `booking-service`'s frozen consumer never receives the event and no booking would ever confirm
— a silent break of a frozen contract this brief explicitly forbids changing (*"Do not change Kafka
contracts"*). The brief's `PaymentSucceeded` is understood as a synonym for the frozen
`PaymentCompleted`; this document uses `PaymentCompleted` everywhere. See `events-published.md`.

### Conflict 2 — Event `PaymentCreated` (brief) is not in the frozen event catalog

The brief asks `payment-service` to publish **`PaymentCreated`**. `docs/architecture/event-catalog.md`'s
"Payment Events" table does **not** list it (it lists `PaymentCompleted`, `PaymentFailed`,
`PaymentTimedOut`, `RefundInitiated`, `RefundCompleted`, `RefundFailed` — no `PaymentCreated`).

**Resolution: publish `PaymentCreated` to `analytics-service` only, and flag it as an additive gap
in the frozen catalog.** This exactly mirrors how the platform already treats `BookingCreated`
(`docs/architecture/event-catalog.md`: *"`BookingCreated` | booking-service | analytics-service | A
booking entered `PENDING_PAYMENT` (funnel tracking)"*) — a low-value, analytics-only funnel signal
that carries no correctness weight. Because it has no existing consumer and no service's behavior
depends on it, adding it breaks nothing. **Recommended smallest change:** add one row to
`event-catalog.md`'s "Payment Events" table (`PaymentCreated | payment-service | analytics-service |
A payment intent was created for a booking`). This document does **not** modify that file; it flags
the addition and proceeds against the analytics-only contract. See `events-published.md`.

**Freeze decision (2026-07-27): `PaymentCreated` is KEPT — as an explicitly informational event.**
The final review considered removing it (it has no consumer that exists today) but decided to keep
it, deliberately, as an **informational / observability** signal. Its contract is now frozen as:

- **Informational only** — intended for analytics, observability, reporting, and future downstream
  consumers (e.g. a future `fraud-service` or `analytics-service`).
- **Carries no business-correctness responsibility.** No booking, payment, or refund outcome depends
  on it; a lost or delayed `PaymentCreated` affects a dashboard, never a payment's state.
- **`booking-service` must never depend on `PaymentCreated`.** The booking flow relies exclusively
  on the frozen business events — `PaymentCompleted`, `PaymentFailed`, `PaymentTimedOut`,
  `RefundCompleted` (and the other frozen refund/booking events) — never on `PaymentCreated`.
- The one carried-forward action is the additive one-row entry in `event-catalog.md` above; keeping
  the event does not change that this document does not modify that frozen file.

See `events-published.md`'s "`PaymentCreated` — Informational Event (Frozen)", `payment-saga.md`, and
`payment-state-machine.md` for the same statement at their level of detail.

### Conflict 3 — The brief's richer payment states vs. `payment-flow.md`'s frozen coarse statuses

The brief asks the state machine to model, *at minimum*, `CREATED`, `PENDING`, `AUTHORIZED`,
`CAPTURED`, `FAILED`, `CANCELLED`, `REFUND_PENDING`, `REFUNDED`, `EXPIRED`. The frozen
`docs/architecture/payment-flow.md` uses a **coarser** vocabulary for the same lifecycle:
`INITIATED`, `COMPLETED`, `FAILED`, `TIMED_OUT`, `REFUND_INITIATED`, `REFUND_COMPLETED`,
`REFUND_FAILED`.

**Resolution: model the richer internal state machine the brief asks for, and map it onto
`payment-flow.md`'s frozen coarse vocabulary — with the *externally emitted events* always using the
frozen event names.** The richer states (`AUTHORIZED` vs. `CAPTURED`, `EXPIRED`, `CANCELLED`) are
`payment-service`-internal domain detail — the natural shape of an auth-then-capture gateway flow —
while the cross-service *events* `booking-service` consumes stay exactly as
`payment-flow.md`/`event-catalog.md` froze them. This is the same move
`docs/services/booking-service/domain-model.md` made: adopt a precise internal model, then prove
every externally-visible name still matches the frozen contract. The full mapping table is in
`payment-state-machine.md`'s "Reconciling the Requested State Vocabulary" and `domain-model.md`; the
brief also **omits** two states/events the frozen catalog *requires* (`TIMED_OUT`/`PaymentTimedOut`
and `RefundFailed`), which this document **adds back**, exactly as `booking-service` added back the
frozen `COMPLETED` state the brief's booking-state list had omitted.

**None of the three resolutions changes an existing frozen contract.** Every event
`booking-service`, `notification-service`, or `analytics-service` already expects is produced under
its frozen name; every state distinction the brief asked for is preserved internally.

## A Fourth, Deeper Conflict — the Refund *Trigger* Is Documented Two Ways

Separately from the vocabulary conflicts above, reading the frozen documents closely surfaced a
genuine **mechanism** conflict about *how a refund is triggered*: `docs/architecture/api-inventory.md`
documents a synchronous service-to-service **Refund API** consumed by `booking-service`, while
`docs/architecture/event-catalog.md` documents `payment-service` **consuming `BookingCancelled`**
"when a refund is owed." Taken naively, having both would risk a double refund. This is resolved in
full in `boundaries.md`'s "Relationship to `booking-service` — the Refund Trigger, Reconciled" (the
synchronous API is the single authoritative initiator; `BookingCancelled` consumption is a
reconciliation safety-net that can never double-refund) and flagged again in `events-consumed.md`.
It is called out here because it is the one conflict with real correctness stakes, not just naming.

## Implementation Readiness

**Yes — this specification is implementation-ready**, with the same kind of explicitly-non-blocking
carried-forward conditions `booking-service`'s own overview documents:

- **The gateway adapters have no live gateway wired yet.** The `PaymentGateway` port and its
  registry are fully specified; a concrete Razorpay/Stripe/Adyen adapter is a configuration-plus-
  adapter-package addition (the exact "contract ready, adapter is the only new code" posture
  `provider-integration-service`'s FlixBus integration already proved). See `domain-model.md`'s
  "Payment Gateway Abstraction."
- **`booking-service` exists and its consumer/caller contracts are frozen** — this service is built
  to satisfy them as-is. The one open coordination point is the saga/outbox formalization that
  `docs/architecture/payment-flow.md` explicitly defers until *both* services are implemented
  (*"that formalization happens when booking-service and payment-service are actually
  implemented"*); this document specifies the outbox posture (`events-published.md`) without fixing
  its mechanics, matching `booking-service`'s identical hedge.
- **`operator-service` does not exist**, but `payment-service` does **not** depend on it — the
  cancellation-policy lookup that needs `operator-service` lives entirely in `booking-service`
  (`docs/services/booking-service/boundaries.md`). `booking-service` passes `payment-service` an
  already-computed refund amount; `payment-service` never sees a policy. This is a dependency this
  service is deliberately insulated from.
- **`PaymentCreated` needs a one-row addition to the frozen event catalog** (Conflict 2). Additive,
  analytics-only, breaks nothing; flagged, not silently applied.

## Documents in This Directory

| Document | Covers |
|---|---|
| `responsibilities.md` | Explicit responsibilities and non-responsibilities |
| `boundaries.md` | Every service relationship reviewed one by one, the refund-trigger reconciliation, and all security boundaries (JWT, webhook signature, replay, audit) |
| `domain-model.md` | `Payment`, `Refund`, attempts, transactions, value objects, the `PaymentGateway` abstraction, invariants, idempotency, retry, webhook verification, optimistic locking |
| `use-cases.md` | Every inbound-port use case — client-facing, service-to-service, webhook, event-driven, scheduled |
| `sequence-diagrams.md` | Success, failure, timeout, late-success, and refund flows |
| `data-ownership.md` | What is authoritative here vs. an opaque reference to another service's data; NFR-12 posture |
| `events-published.md` | `PaymentCreated`, `PaymentCompleted`, `PaymentFailed`, `PaymentTimedOut`, `RefundInitiated`, `RefundCompleted`, `RefundFailed` |
| `events-consumed.md` | `BookingCancelled` (reconciliation only), and why `BookingCreated` is deliberately not consumed |
| `api-summary.md` | Client-facing, internal (refund), and the public webhook surface |
| `payment-state-machine.md` | The full state machine — every transition, the frozen-vocabulary mapping, and the Mermaid diagram |
