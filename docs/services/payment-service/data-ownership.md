# Payment Service — Data Ownership

## What This Service Owns

One Postgres database (`docs/architecture/database-ownership.md`), holding `Payment` (with its
`PaymentAttempt` and `PaymentTransaction` entities), `Refund` (with `RefundAttempt`), `WebhookEvent`,
`PaymentAuditRecord`, and `ReconciliationRecord`. No service reads this database but
`payment-service` — the same hard rule every service on the platform follows
(`docs/architecture/database-ownership.md`, NFR-9).

Redis may optionally front webhook-replay/idempotency lookups for latency, but — as with every other
service's cache on this platform — it must be **cache-only, never a system of record**; the unique
constraints on `Payment.idempotencyKey` and `WebhookEvent.gatewayEventId` in Postgres are the
authoritative de-duplication, exactly the unconditional rule `inventory-service`'s and
`booking-service`'s `data-ownership.md` state for their own situations. If Redis is flushed, the
platform degrades in latency, not correctness (`docs/architecture/high-level-design.md` §7).

## The One Thing This Service Must Never Store — Raw Payment Instrument Data

**`payment-service` never stores raw card, bank, or payment-instrument data. Not encrypted, not
tokenized-by-us, not "just the last four" beyond what a gateway explicitly returns as safe — none of
it.** This is NFR-12 (*"RoadScanner never stores raw payment card data; payment processing is
delegated to a PCI-DSS-compliant payment gateway"*) and `docs/architecture/high-level-design.md` §8
(*"Payment data never enters RoadScanner's own database — payment-service stores only gateway
references and status"*). What this service persists is **gateway references** (`gatewayPaymentId`,
`gatewayOrderId`, `gatewayRefundId`), **status**, **amounts**, and **audit metadata** — the pointers
and outcomes, never the instrument. This is what keeps RoadScanner out of PCI cardholder-data scope,
and it is the single most important data-ownership rule for this service. The gateway is the system
of record for the instrument; RoadScanner holds only what it needs to correlate and reconcile.

## Kinds of Authority

| Data | Authority | Why |
|---|---|---|
| `Payment` (status, amount, attempts, gateway references, timestamps) | **Fully authoritative — owned outright, no upstream source** | This is the platform's only record of a payment's lifecycle. No other service tracks any of it. |
| `Refund` / `RefundAttempt` | **Fully authoritative, owned outright** | The platform's only record of a refund's own lifecycle — `booking-service` deliberately does **not** track it (`docs/services/booking-service/booking-state-machine.md`'s "Future Refund — Deliberately Not a State"). |
| `PaymentTransaction` (internal ledger) | **Fully authoritative, insert-only** | The *"internal ledger of transactions"* `docs/architecture/service-boundaries.md` assigns here — the authoritative record of money that moved. The input a future accounting/settlement service reads, not settlement itself (`boundaries.md`). |
| `WebhookEvent`, `PaymentAuditRecord`, `ReconciliationRecord` | **Fully authoritative, insert-only** | Idempotency, security audit, and reconciliation records — owned outright, never mutated. |
| `BookingReference` | **Opaque reference — not owned** | `booking-service`'s. Used only to key events and correlate webhooks/refunds; never dereferenced against `booking-service`'s database (`docs/architecture/database-ownership.md`). Mirrors `booking-service`'s own opaque `paymentReference` into this service. |
| Refund eligibility / cancellation policy / refund amount | **Not owned, not stored as authority** | `booking-service` / `operator-service`'s. The `Refund.amount` this service stores was *computed by `booking-service`* and handed over — this service records what it was told to refund, it does not own the rule that produced the number. |
| Card / bank / instrument data | **Never stored at all** (NFR-12) | The external gateway is the system of record. |
| Trip, fare source, seat state | **Not modeled here at all** | `inventory-service` / `provider-integration-service`. The `Payment.amount` is a frozen `Money` value passed in, not a live-tracked fare. |

## The Amount Is Frozen, Like a Booking's Fare

A `Payment`'s `amount` is captured at initiation from what `booking-service` quoted (itself frozen
from `inventory-service`'s `FareSnapshot` at hold time —
`docs/services/booking-service/data-ownership.md`) and is **immutable** thereafter. This is the same
"a commitment made at a point in time, not a live-tracked value" posture `booking-service` takes
toward fare, carried one step further: the traveler is charged the amount they committed to, and
that amount never silently drifts because a catalog fare changed later. `payment-service` never
re-reads a fare from anywhere — it has no relationship with `inventory-service` at all
(`boundaries.md`).

## Payment ↔ Booking: Two Services, Two Opaque References, No Shared Table

`booking-service` holds a `paymentReference` (opaque, into this service's ledger); `payment-service`
holds a `bookingReference` (opaque, into `booking-service`'s data). **Neither reads the other's
database.** Cross-service consistency is achieved through events (`PaymentCompleted`/`PaymentFailed`/
`PaymentTimedOut`, `BookingCancelled`) and the synchronous refund call, plus — on this one
strongly-consistent path — a transactional outbox in each service
(`docs/architecture/high-level-design.md` §6, NFR-10). This is `docs/architecture/database-ownership.md`'s
"Booking ↔ Payment Exception, Clarified" seen from the payment side: *"tight application-level
coordination is not a database exception. Each still owns its own database."*

## Retention

**Payments, refunds, ledger lines, and audit records are never deleted, at any status.** Financial
and audit records must remain queryable indefinitely for support (FR-8.3), dispute resolution,
reconciliation, and compliance (NFR-13) — the same "never deleted" posture `booking-service` takes
toward `Booking` rows, for the same reasons, applied to money records where it matters even more.
`WebhookEvent` rows are the one genuinely transient category and may be pruned on a long schedule
once well past any replay window (`use-cases.md`'s "Sweep Stale Webhook Records") — but never the
`Payment`, `Refund`, ledger, or audit rows.

## Encryption

Sensitive references and PII this service does hold (gateway references, and the `travelerId` /
`bookingReference` correlation) are **encrypted at rest and in transit** (NFR-13). This is a
mitigation *on top of* — never a substitute for — the NFR-12 rule that raw instrument data is not
stored here in the first place. In-transit protection covers both the client→`payment-service` leg
(via `api-gateway`/TLS) and the `payment-service`→gateway leg (the gateway's own TLS).

## Rebuildability

**Not rebuildable from any other service's data.** A `Payment` is the original source of truth for
its own lifecycle — no event log elsewhere on the platform can reconstruct which payments succeeded,
which refunds completed, or what the ledger says. `booking-service` never learns a payment's gateway
references; the gateway holds the instrument but not RoadScanner's own correlation and audit trail.
This is precisely why `docs/architecture/high-level-design.md` §6 treats the booking↔payment path as
the one place on the platform needing saga/outbox-grade consistency rather than "eventually
consistent, rebuildable if wrong" — a lost payment record, like a lost booking record, cannot be
reconstructed from anywhere else, and NFR-7's "no lost payment" leaves no room for it.

## Explicitly Not Designed Here

Physical Postgres schema, the exact idempotency-constraint mechanism, the transactional outbox's
table shape and publishing mechanism (`docs/architecture/high-level-design.md` §6's deferred
formalization), the per-gateway signing-secret storage mechanism (a secrets-management decision),
and how long `WebhookEvent` payload digests are retained — all implementation decisions made when
this service is actually built, not architecture decisions, matching every other service's
`data-ownership.md` in this documentation set.
