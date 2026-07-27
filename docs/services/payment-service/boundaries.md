# Payment Service — Boundaries

This deepens `docs/architecture/service-boundaries.md`'s `payment-service` entry with this
service's own relationship decisions, at the level of detail
`docs/services/booking-service/boundaries.md` and
`docs/services/provider-integration-service/boundaries.md` already established. Every relationship
is reviewed explicitly — including the ones that turn out to be "no relationship," and the ones that
are "designed for, not yet real" — and this document also carries this service's **security
boundaries** (JWT, webhook signature, replay protection, audit), because the brief's security
concerns are boundary concerns.

## The Central Design Point: Payment Service Owns Money Movement, Never the Reason for It

| Concern | Owner | Why not `payment-service` |
|---|---|---|
| Whether a booking should be paid for, and for what amount | `booking-service` (quoting the frozen fare) | `payment-service` charges what it's told; it has no concept of a trip or a fare |
| Whether a refund is owed, and for how much | `booking-service` / `operator-service` policy | `payment-service` executes refunds it's told to execute (`docs/architecture/service-boundaries.md`) |
| How the seat is held, confirmed, or released | `provider-integration-service` / `booking-service` | `payment-service` has no concept of a seat |
| The payment/refund transaction, its status, its gateway references, its ledger | **`payment-service`** | This is the one thing no other service has any reason to own |

`payment-service` is the mirror image of `booking-service`'s "owns the outcome, not the mechanics":
`payment-service` owns the *money mechanics*, never the *booking outcome* they feed into.

## Relationship to `booking-service`

This is the primary relationship, and it has **four distinct channels**, each reviewed below. It is
the one place on the platform, per `docs/architecture/high-level-design.md` §6, where two services
need saga/outbox-grade strong consistency rather than best-effort eventual consistency (NFR-10).

### Channel 1 — Client pays `payment-service` directly (synchronous, client-facing)

`docs/architecture/booking-flow.md` step 3 and `docs/architecture/api-inventory.md`'s
`payment-service` row both freeze this: the client (`customer-web`) calls `payment-service`
**directly** to initiate payment against a `PENDING_PAYMENT` booking — **not** through
`booking-service`. `docs/services/booking-service/boundaries.md`'s "Relationship to `payment-service`"
already explains, from the other side, why this does not break `booking-service`'s single-orchestrator
ownership: the client is only *submitting payment* to the service that owns payment submission, not
orchestrating the booking outcome. PCI-scope reasoning (NFR-12) argues *against* routing payment
detail through an extra `booking-service` hop that would then be in cardholder-data scope. This
document simply confirms the same decision from the payment side: `payment-service` is a
first-class client-facing service for Payment Initiation and Payment Status, reached through
`api-gateway` (`api-summary.md`).

### Channel 2 — `payment-service` informs `booking-service` of outcomes (asynchronous, events)

`payment-service` publishes `PaymentCompleted` / `PaymentFailed` / `PaymentTimedOut`
(`events-published.md`); `booking-service` consumes them to drive its state machine
(`docs/services/booking-service/events-consumed.md`). `payment-service` **never calls
`booking-service` synchronously** and never learns the booking's state — it emits what happened to
the money and moves on. This is the correct direction of the dependency: the money outcome is a
fact `booking-service` reacts to, exactly as `docs/architecture/booking-flow.md` steps 4–5 describe.

### Channel 3 — `booking-service` requests a refund (synchronous, service-to-service, inbound)

`booking-service` calls `payment-service`'s internal **Refund API** (`api-summary.md`) to execute a
refund it has decided is owed, passing the payment reference, the **amount it computed**, and an
idempotency key. `payment-service` executes it and tracks the refund's own lifecycle. See the
reconciliation section immediately below for why this synchronous call — not the `BookingCancelled`
event — is the authoritative trigger.

### Channel 4 — `payment-service` reconciles against `BookingCancelled` (asynchronous, safety-net)

`payment-service` consumes `BookingCancelled` **for reconciliation only** — see below.

### Relationship to `booking-service` — the Refund Trigger, Reconciled (a Discovered Conflict)

Reading the frozen documents surfaced a real **mechanism conflict** about how a refund is triggered:

- `docs/architecture/api-inventory.md`'s `payment-service` row documents a **synchronous** Refund
  API: *"Refund | Trigger and track a refund | booking-service (service-to-service), admin-console
  (support overrides)."*
- `docs/architecture/payment-flow.md`'s "Refund Handling" diagram shows a **direct call**:
  *"B->>P: request refund (payment reference)."*
- `docs/services/booking-service/boundaries.md` says `booking-service` *"calls a refund-request
  port whose adapter has no real target yet"* — a synchronous call.
- **But** `docs/architecture/event-catalog.md` also lists `payment-service` as a **consumer of
  `BookingCancelled`** (*"payment-service consumes this only when a refund is owed"*).

Taken naively, a refund could be triggered *both* by the synchronous call *and* by consuming the
event — a double-refund hazard. Per the brief's "AMBIGUITIES" instruction (*"Stop. Explain the
conflict. Recommend the smallest possible change."*), here is the resolution, chosen to keep **both**
frozen statements literally true without any double refund:

- **The synchronous Refund API is the single authoritative initiator.** It is the only channel that
  carries the refund **amount** — which `booking-service` computes from `operator-service`'s
  cancellation policy (traveler-initiated) or as a full refund (trip-cancelled). `payment-service`
  cannot compute a policy-based amount from `BookingCancelled` alone, because it does not own policy
  (`docs/architecture/service-boundaries.md`: *"the decision of whether a refund is owed... that's a
  booking-service/operator-service policy decision"*). So the event **cannot** correctly be the
  primary trigger for a partial refund — the synchronous call must be.
- **`BookingCancelled` consumption is a reconciliation safety-net, not a second initiator.**
  `payment-service` consumes it to *detect* refund-eligible cancellations (identifiable by
  `cancellationReason` plus the existence of a captured `Payment`) and verify that a corresponding
  `Refund` already exists — initiated via the synchronous API. If, after a grace window, a
  captured payment for a refund-eligible cancellation has **no** `Refund`, `payment-service` raises
  a **reconciliation discrepancy for support** (a `ReconciliationRecord`), never a blind automatic
  refund of an amount it cannot compute. Because both channels key on the same
  `(paymentReference / bookingReference)` and the refund idempotency key, the reconciliation
  consumer can **never** create a second refund — if one already exists, it is a no-op.
- **This keeps every frozen statement true:** `payment-service` *does* consume `BookingCancelled`
  "when a refund is owed" (to reconcile), and `booking-service` *does* call a synchronous
  refund-request port. No frozen contract changes.

**Recommended smallest change (not applied here):** a future revision of
`docs/architecture/event-catalog.md` could add one clause clarifying that
`payment-service`'s consumption of `BookingCancelled` is **reconciliation-only**, not a refund
initiator — removing the ambiguity at its source. This document flags it; it does not modify that
file.

**Failure mode (Channel 3).** If `payment-service` is unreachable when `booking-service` requests a
refund, `booking-service` retries (its adapter's normal retry). The refund idempotency key ensures
those retries create at most one `Refund`. If the refund request is lost entirely (e.g., a crash
between `booking-service`'s cancellation and its refund call), the Channel-4 reconciliation consumer
is exactly the net that catches it — a captured payment whose booking is cancelled with no refund,
surfaced to support. This is the payment-side half of the "no lost payment" guarantee (NFR-7).

## Relationship to `auth-service`

**No synchronous call, but not "none" either** — identical to `booking-service`'s posture. Per
`docs/architecture/authentication-flow.md`'s "Service-to-Service Calls" and `auth-service`'s
defense-in-depth model, `payment-service` never phones `auth-service` at request time; it
independently validates the JWT signature (against `auth-service`'s published public key) and reads
its claims locally. `api-gateway` authenticates; `payment-service` re-checks authorization at its
own boundary (NFR-11). See "Payment ↔ Auth" below.

## Payment ↔ Auth: Ownership Enforcement and the Public Webhook Boundary

Every **client-facing** operation requires an authenticated identity (`docs/architecture/authentication-flow.md`,
NFR-11). `payment-service` enforces authentication and authorization independently, per
`auth-service`'s defense-in-depth model:

- **Authentication** — done by `api-gateway`; `payment-service` re-validates the token's
  signature/expiry at its own boundary rather than trusting the gateway's check alone
  (`docs/services/auth-service/security-design.md`'s stated rule, applied here).
- **Authorization** — role- and ownership-based, decided within `payment-service` using only its
  own data:
  - **`TRAVELER`** — may initiate a payment for their own booking, and view the status of a payment
    where `Payment.travelerId` equals the token's subject. Accessing another traveler's payment is a
    `404`, not a `403` — the same enumeration-protection posture `booking-service` and `auth-service`
    apply, so a traveler cannot distinguish "not yours" from "doesn't exist."
  - **`ADMIN` / `SUPPORT`** — may view any payment/refund and trigger a support-override refund
    (`docs/architecture/api-inventory.md`'s *"admin-console (support overrides)"*), backing FR-8.3's
    support-lookup journey. A support-triggered refund is the *same* idempotent refund operation a
    booking-driven refund uses — support resolves issues by triggering the same operation, not via a
    separate privileged money-movement path, keeping the number of ways money can move to a minimum
    (the same principle `booking-service` applies to its state machine's entry points).
  - **`OPERATOR`** — has **no** client-facing payment operation in Phase 1. An operator's
    settlement/payout view (FR-5.6) is a future accounting concern (below), not a `payment-service`
    client endpoint.

**The one public, JWT-less endpoint — the gateway webhook.** The webhook endpoint
(`api-summary.md`) is called by the external payment gateway, which has no RoadScanner JWT. It is
therefore **not** authenticated by JWT; it is secured by **gateway signature verification**
(`domain-model.md`'s "Webhook Verification"): every webhook's signature is verified against the
gateway's signing secret before any state change, its `gatewayEventId` is checked for replay, and a
failed verification is audited and rejected. This is the payment analogue of
`provider-integration-service`'s and `search-service`'s disclosed `/internal/**` posture — a
non-JWT surface secured by a different, appropriate mechanism, with the boundary stated explicitly
rather than left implicit. `api-gateway` must route the webhook path to `payment-service` **without**
applying JWT enforcement (an `api-gateway` routing decision, not designed here since that service
doesn't exist yet), and must never expose the internal Refund API publicly.

## Relationship to `provider-integration-service`

**None — no relationship at all.** `payment-service` has no concept of a seat, a provider, or a
reservation, and never calls `provider-integration-service`. The TTL discipline that guarantees a
seat hold outlives payment processing (`docs/architecture/seat-locking-flow.md`,
`docs/architecture/booking-flow.md` step 4) is entirely `booking-service`'s and
`provider-integration-service`'s concern; `payment-service` is unaware of it. Stated explicitly, for
the same reason `booking-service` states its own "none" relationships (`search-service`) precisely
rather than omitting them.

## Relationship to `inventory-service` and `search-service`

**None, in either direction.** `payment-service` never touches catalog, trip, fare-source, or search
data. The fare a payment charges was frozen by `booking-service` at hold time
(`docs/services/booking-service/data-ownership.md`) and passed to `payment-service` as a plain
`Money` amount — `payment-service` never re-reads it from `inventory-service` and has no reason to.

## Relationship to `notification-service` (Not Yet Built)

**Asynchronous, one direction, event-only.** `notification-service` consumes `RefundCompleted` and
`RefundFailed` (`docs/architecture/event-catalog.md`) to send the traveler a refund confirmation or,
for a failed refund, to support the manual-handling path (FR-6.2). `payment-service` never calls
`notification-service` and never decides notification content or timing
(`docs/architecture/service-boundaries.md`'s `notification-service` entry). Note that **payment
*success* is not notified by `payment-service`** — `booking-service`'s `BookingConfirmed` drives the
booking-confirmation message (`docs/architecture/event-catalog.md`: `PaymentCompleted` consumers are
`booking-service` and `analytics-service`, not `notification-service`). This is deliberate: the
traveler is told "your booking is confirmed," a fact only `booking-service` can assert, not "your
payment succeeded" in isolation. No degradation of `notification-service` can block a payment (NFR-8)
— there is no synchronous path between the two.

## Relationship to `analytics-service` (Not Yet Built)

**Asynchronous, one direction, event-only.** `analytics-service` consumes every payment and refund
event (`PaymentCreated`, `PaymentCompleted`, `PaymentFailed`, `PaymentTimedOut`, `RefundInitiated`,
`RefundCompleted`, `RefundFailed`) for funnel and revenue reporting
(`docs/architecture/event-catalog.md`; FR-8.2). Same non-blocking posture as `notification-service`.

## Relationship to a Future `fraud-service` (Not Planned in Phase 1)

**None today; designed to accommodate one later without change.** Fraud scoring is deliberately
**not** embedded in `payment-service` (`responsibilities.md`). If a `fraud-service` is built, the
natural integration is asynchronous: it consumes `PaymentCreated`/`PaymentCompleted` for scoring and
reporting, exactly as `analytics-service` does — never a synchronous in-line dependency on the
payment critical path, which NFR-8's "non-critical services must never block the booking path"
principle would forbid. A future **synchronous** pre-authorization fraud check (block a payment
before it starts) would be a deliberate addition to the `Initiate Payment` use case, made when that
service is actually planned — this document anticipates the seam (the `PaymentCreated` event, and a
`CANCELLED` transition already exists for a payment stopped before capture) but does not design the
integration, matching how `booking-service` anticipates but does not design its future
`operator-service` integration.

## Relationship to a Future Accounting / Settlement Service (Not Planned in Phase 1)

**None today; this service produces the data such a service would read.** `payment-service`'s
internal ledger (`PaymentTransaction`) is the authoritative record of money that moved; an operator
settlement/payout view (FR-5.6) — computing what each operator is owed, net of commission, per
settlement cycle — is a **separate** accounting concern, not this service's ledger. This distinction
matters because `docs/architecture/service-boundaries.md` gives `payment-service` "an internal
ledger of transactions," which could be misread as owning settlement. It does not: it owns the
transaction record; settlement is the derivation of payouts *from* that record, a future service's
job. Flagged explicitly so the boundary is not silently absorbed here.

## What's Deliberately Out of Scope

- **Any gateway-specific logic, SDK, or resilience policy outside the adapter package** — see
  `domain-model.md`'s "Payment Gateway Abstraction" and "Non-Responsibilities" in
  `responsibilities.md`. The domain never knows which gateway is used. This isolation is
  **recommended to be enforced by an automated architecture test** (ArchUnit-style: domain imports
  no Spring and no gateway SDK; only `adapter.out.gateway..` depends on a gateway implementation; no
  gateway-specific type leaks outside its adapter package) — specified in
  `domain-model.md`'s "Architecture Test (Recommended)", to be implemented when the service is built.
- **Cancellation-policy computation and refund-amount calculation** — `booking-service`/
  `operator-service`'s, unchanged by this specification.
- **A saga/outbox implementation's exact mechanics** — `docs/architecture/high-level-design.md` §6
  and `docs/architecture/payment-flow.md` name the target (transactional outbox on the
  booking↔payment path) and explicitly defer its formalization until both services are implemented;
  the exact outbox table shape and publishing mechanism are implementation decisions, the same hedge
  `booking-service` applies (`docs/services/booking-service/events-published.md`).
- **Cross-vertical payments** (train, flight) — Phase 2+; `payment-service` is a shared-concept
  service reused as-is (`docs/architecture/high-level-design.md` §12), and nothing here assumes
  anything bus-specific.
