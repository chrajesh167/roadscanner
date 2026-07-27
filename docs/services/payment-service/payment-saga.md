# Payment Service — Distributed Transactions (Saga)

This document maps every distributed transaction that touches money on the RoadScanner platform —
the interactions spanning `booking-service`, `payment-service`, the external gateway, gateway
webhooks, Kafka, refunds, and timeouts. It consolidates, from the payment side, the flow
`docs/architecture/booking-flow.md` and `docs/architecture/payment-flow.md` already define
cross-service, now that both participating services have full specifications.

It documents the **target saga design**; it does not redesign the frozen consistency approach.
`docs/architecture/payment-flow.md` is explicit that the coordination is **choreographed today**
(each service reacts to the other's events), that an **explicit saga is deliberately deferred**
until both services are actually implemented, and that its likely eventual form is **orchestrated**,
"given `booking-service` is already the natural owner of the outcome." This document respects that:
it shows the saga's steps, compensations, and atomic boundaries so implementation has a single
consolidated reference, while the orchestration-vs-choreography formalization remains the open,
deferred decision those frozen documents own — see "Consistency Model" below.

## Consistency Model (Recap — Not Redesigned Here)

- **The booking↔payment path is the one strongly-consistent path on the platform** (NFR-10,
  `docs/architecture/high-level-design.md` §6). Everywhere else, eventual consistency via Kafka is
  acceptable; here, "no double-booking, no lost payment" (NFR-7) and "a failed/partial payment must
  never leave a booking inconsistent" (FR-4.3) forbid it.
- **Each participating service uses a transactional outbox** so its own database write and its Kafka
  publish commit atomically (`docs/architecture/high-level-design.md` §6;
  `docs/services/payment-service/events-published.md`). There is no window where a service's own
  state and its published event disagree.
- **Today: choreography.** `payment-service` publishes `PaymentCompleted`/`PaymentFailed`/
  `PaymentTimedOut`; `booking-service` reacts. `booking-service` calls `payment-service`'s
  synchronous `Initiate Refund` on a refund-eligible cancellation. Each reacts to the other; no
  central orchestrator process exists yet.
- **Later: likely orchestration.** `docs/architecture/payment-flow.md` names `booking-service` as
  the natural orchestrator when this is formalized. That formalization is **out of scope here** —
  this document is the map of the transaction, not the orchestrator's implementation.

Each participant owns a distinct, non-overlapping slice, which is what makes the saga tractable:

| Participant | Owns in the saga | Never does |
|---|---|---|
| `booking-service` | Booking state, the refund *decision* and *amount*, seat orchestration | Compute payment state; call the gateway |
| `payment-service` | Payment/refund state, gateway interaction, money movement, the ledger | Decide *whether* a refund is owed; touch booking or seat state |
| `provider-integration-service` | Seat block/confirm/release with the provider | Anything about money |
| External gateway | Authorization, capture, refund of funds | Anything about RoadScanner's own records |

## Saga 1 — Booking–Payment Forward Saga (the Critical Path)

The core distributed transaction: turn a held seat into a confirmed, paid booking, or unwind cleanly
if any step fails. Steps 1–3 are `booking-service`'s (`docs/architecture/booking-flow.md`); this
document details steps 4–7 where money and the gateway enter.

```mermaid
sequenceDiagram
    participant T as Traveler
    participant BS as booking-service
    participant PIS as provider-integration-service
    participant PS as payment-service
    participant PG as Gateway (external)
    participant K as Kafka

    T->>BS: hold seat + create booking (PENDING_PAYMENT)
    BS->>PIS: BlockSeat (TTL > max payment time)
    T->>PS: Initiate Payment (bookingReference, amount, Idempotency-Key)
    PS->>PS: create Payment (CREATED), publish PaymentCreated
    PS->>PG: start transaction (via PaymentGateway port)
    PG-->>PS: (async) webhook: captured
    PS->>PS: verify + de-dupe; Payment → CAPTURED; ledger CAPTURE line
    PS->>K: publish PaymentCompleted   %% outbox: state+event atomic
    K->>BS: deliver PaymentCompleted
    BS->>PIS: ConfirmBooking
    alt provider confirms
        PIS-->>BS: providerBookingReference + ticket
        BS->>BS: Booking → CONFIRMED, publish BookingConfirmed
    else provider rejects after payment (rare — TTL makes it near-unreachable)
        BS->>BS: Booking → CANCELLED (PROVIDER_CONFIRMATION_FAILED), supportFlagged
        BS->>PS: Initiate Refund (automatic)  %% compensation — see Saga 2
    end
```

**`PaymentCreated` is not a saga step.** It appears in the diagram above only as an informational
emission (`events-published.md`'s "`PaymentCreated` — Informational Event (Frozen)") — no saga
participant waits on it, branches on it, or compensates for its absence. It is safe to delete it from
a mental model of the saga entirely: the transaction's correctness rests solely on the business
events (`PaymentCompleted`/`PaymentFailed`/`PaymentTimedOut`) and the synchronous calls. `PaymentCreated`
is reporting, not coordination.

### Compensation Matrix — Saga 1

Every step has a defined forward action and a defined compensation, which is what makes it a saga
rather than a hopeful sequence:

| Step | Forward action | Failure | Compensation | Money moved? |
|---|---|---|---|---|
| Block seat | `provider-integration-service.BlockSeat` | Seat unavailable / provider down | None needed — no booking, no payment created | No |
| Create booking | `booking-service` persists `PENDING_PAYMENT` | — | Hold released / expires on TTL | No |
| Initiate payment | `payment-service` creates `Payment`, calls gateway | Gateway declines | `Payment → FAILED` → `PaymentFailed` → `booking-service` cancels booking, releases seat | No |
| Capture | Gateway captures; `Payment → CAPTURED` | No outcome in window | `Payment → EXPIRED` → `PaymentTimedOut` → booking cancelled (**Saga 3** covers late arrival) | No (until late-success) |
| Emit outcome | Outbox publishes `PaymentCompleted` | Publish/consume lag | Kafka retains; `booking-service` catches up from offset | Already moved; booking confirms late |
| Confirm with provider | `booking-service.ConfirmBooking` | Provider rejects post-payment | `booking-service` → `Initiate Refund` (**Saga 2**) + `supportFlagged` | Yes → **refunded** |

**Why the confirm step's failure is safe:** the seat-hold TTL is deliberately longer than the max
payment-processing time (`docs/architecture/seat-locking-flow.md`), so the confirm step almost never
fails for a lost hold. When it does fail for any other reason, the compensation is a refund — the
platform's universal "never silently keep the traveler's money" rule
(`docs/architecture/payment-flow.md`, `docs/architecture/booking-flow.md` step 4).

## Saga 2 — Refund Saga (Cancellation Compensation)

Every refund-eligible cancellation runs this sub-saga. Its trigger is always
`booking-service`'s synchronous `Initiate Refund` call (`boundaries.md`'s "the Refund Trigger,
Reconciled"); the entry conditions differ but the money mechanics are identical.

**Entry conditions (all route to the same `Initiate Refund`):**

- Traveler cancels a `CONFIRMED` booking (amount per `operator-service` policy).
- `TripCancelled` cascade (full refund regardless of policy — `docs/architecture/booking-flow.md`
  step 7).
- Provider confirmation fails after payment (Saga 1's `else` branch).
- Late payment success after a timeout cancellation (Saga 3).
- Admin/support override (same idempotent endpoint).

```mermaid
sequenceDiagram
    participant BS as booking-service
    participant PS as payment-service
    participant PG as Gateway (external)
    participant K as Kafka
    participant N as notification-service

    BS->>PS: Initiate Refund (paymentReference, amount, reason, Idempotency-Key)
    alt Refund already exists for this key
        PS-->>BS: existing Refund (idempotent no-op)
    else new
        PS->>PS: create Refund (REQUESTED); Payment → REFUND_PENDING (full)
        PS->>K: publish RefundInitiated   %% outbox
        PS->>PG: refund request
        alt gateway confirms
            PG-->>PS: refund confirmed (webhook)
            PS->>PS: Refund → COMPLETED; Payment → REFUNDED; ledger REFUND line
            PS->>K: publish RefundCompleted
            K->>N: refund confirmation (FR-6.2)
        else gateway fails
            PG-->>PS: refund failed
            PS->>PS: Refund → FAILED; Payment → CAPTURED (money did not return)
            PS->>K: publish RefundFailed
            K->>N: routed to support (manual handling)
        end
    end
```

**No compensation follows a `RefundFailed`** — it is not auto-retried indefinitely; it routes to
support (`docs/architecture/payment-flow.md`: *"a stuck refund is a customer-trust issue that needs
a human"*). The booking remains `CANCELLED` throughout; `booking-service.status` never depends on the
refund's outcome (`docs/services/booking-service/booking-state-machine.md`'s "Future Refund —
Deliberately Not a State").

## Saga 3 — Timeout & Late-Success Reconciliation Saga (the Hard One)

The one saga where the gateway's answer can arrive *after* the platform gave up on it. This is
`docs/architecture/payment-flow.md`'s explicitly-required reconciliation path, not an edge case to
hand-wave.

```mermaid
sequenceDiagram
    participant PS as payment-service
    participant PG as Gateway (external)
    participant K as Kafka
    participant BS as booking-service

    Note over PS: no gateway outcome by Payment.expiresAt
    PS->>PS: sweep — Payment → EXPIRED
    PS->>K: publish PaymentTimedOut
    K->>BS: deliver
    BS->>BS: Booking → CANCELLED (PAYMENT_TIMED_OUT); release seat
    Note over PG,PS: ... later, the gateway finally answers ...
    PG->>PS: late webhook: captured
    PS->>PS: record CAPTURED for financial accuracy (money moved); ledger CAPTURE line
    PS->>K: publish PaymentCompleted
    K->>BS: deliver for an ALREADY-CANCELLED booking
    BS->>BS: automatic refund + supportFlagged (never silent keep-the-money)
    BS->>PS: Initiate Refund  --> Saga 2
```

**Why this cannot lose or double-spend money:**

- `EXPIRED` is terminal; the late webhook does **not** revert it. The status stays `EXPIRED`; only
  the **ledger** and the **event** reflect the late capture (`payment-state-machine.md`'s "The
  Late-Success-After-Timeout Interaction").
- The money that moved is recorded truthfully (ledger), so it can never be silently kept — the
  captured amount is visible and reconciled.
- `booking-service` turns the late `PaymentCompleted`-for-a-cancelled-booking into an ordinary
  refund (Saga 2), which is idempotent, so even a duplicate late webhook cannot double-refund.
- If the `Initiate Refund` call itself were lost, the `BookingCancelled` reconciliation consumer
  (`events-consumed.md`) catches a captured payment with no refund and flags it — the final net.

## Atomic Boundaries (Where the Outbox Sits)

A saga is only safe if each local step is atomic with its outgoing signal. The outbox boundaries:

| Service | Atomic unit (one DB transaction) | Published atomically with it |
|---|---|---|
| `payment-service` | `Payment`/`Refund` state transition **+** `PaymentTransaction` ledger line **+** outbox row | `PaymentCreated` / `PaymentCompleted` / `PaymentFailed` / `PaymentTimedOut` / `RefundInitiated` / `RefundCompleted` / `RefundFailed` |
| `booking-service` | `Booking` state transition **+** outbox row | `BookingCreated` / `BookingConfirmed` / `BookingCancelled` |

The synchronous calls in the saga (`Initiate Payment`, `Initiate Refund`, `ConfirmBooking`) are
**not** outbox-published — they are request/response, made idempotent by their idempotency keys so a
retry after an ambiguous failure is safe. The gateway webhook is made idempotent by `gatewayEventId`
de-duplication. Kafka delivery is at-least-once; every consumer is idempotent
(`docs/architecture/event-catalog.md`). Together these close every "did it commit?" gap in the saga.

## What Each Guarantee Buys

| Requirement | How the saga satisfies it |
|---|---|
| **NFR-7** (no lost payment, no double-booking; correctness > availability) | Every money step has a compensation; the ledger records every movement truthfully; reconciliation catches the residual lost-call case |
| **NFR-10** (booking↔payment strongly consistent) | Transactional outbox per service; no state/event disagreement window |
| **FR-4.3** (no charged-without-confirmed, no confirmed-without-payment) | Booking confirms only on `PaymentCompleted`; a post-payment confirm failure compensates with a refund; a timeout compensates by cancelling |
| **FR-4.2** (automatic refund when refund-eligible) | Saga 2, triggered by `booking-service`'s decision, executed idempotently by `payment-service` |

## Explicitly Not Designed Here

The orchestrator's implementation (choreography vs. an explicit orchestrator process — deferred by
`docs/architecture/payment-flow.md` to when both services are implemented), the outbox's physical
table shape and polling/publishing mechanism, retry-interval and timeout numbers, and the gateway's
own internal retry semantics. All are implementation decisions, consistent with every other document
in this set and with `docs/architecture/high-level-design.md` §6's own deferral.

## Cross-References

- `docs/architecture/payment-flow.md`, `docs/architecture/booking-flow.md`,
  `docs/architecture/high-level-design.md` §6 — the frozen cross-service flows this consolidates.
- `payment-state-machine.md` — the payment/refund transitions each saga step drives.
- `sequence-diagrams.md` — the same flows at the single-service call level.
- `boundaries.md` — the refund-trigger reconciliation that guarantees Saga 2 has one initiator.
- `events-published.md` / `events-consumed.md` — the outbox events and the reconciliation consumer.
