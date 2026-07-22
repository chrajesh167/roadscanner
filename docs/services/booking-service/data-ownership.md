# Booking Service — Data Ownership

## What This Service Owns

One Postgres database (`docs/architecture/database-ownership.md`), holding `Booking`, `SeatHold`,
and their embedded value objects (`Passenger`, `Ticket`). No Redis is required by anything in this
specification — unlike `provider-integration-service`'s session cache or
`search-service`'s result cache, nothing here is read-heavy enough or volatile enough to need one;
`SeatHold` rows are already short-lived Postgres rows with a TTL-like field (`expiresAt`), not a
cache in front of anything. If a future implementation adds one anyway, it must be cache-only,
never a system of record — the same unconditional rule `inventory-service`'s own
`data-ownership.md` states for its identical situation.

## Four Different Kinds of Authority, Only One of Them "Owned Outright"

| Data | Authority | Why |
|---|---|---|
| `Booking` (status, passengers, timestamps, cancellation reason, ticket) | **Fully authoritative — owned outright, no upstream source** | This is the platform's only record of a booking. No other service tracks any of this. |
| `SeatHold` | **Fully authoritative, but deliberately transient** | Exists only to bridge the gap between placing a hold and either converting it into a `Booking` or discarding it — see `domain-model.md`. Not "booking history" in any sense; nothing about FR-1.3 depends on a `SeatHold` outliving its own short lifecycle. |
| Trip shape, fare, `ProviderMapping` (`providerType`/`providerTripId`) | **Captured copy, fixed at hold time — not authoritative, never refreshed** | Sourced from `inventory-service`. A booking's fare is what was quoted when the seat was held, not whatever the catalog says later — the copy is deliberately frozen, not "kept current," unlike every other kept-current copy on this platform (`search-service`'s index, `inventory-service`'s first-party `Trip` rows). This is intentional: a traveler's charged amount must never silently drift after they've committed to it. |
| Provider-issued `Ticket` content | **Owned outright, captured once** | `provider-integration-service` explicitly does not persist tickets (`docs/services/provider-integration-service/boundaries.md`: *"nothing here answers 'what did we last book with FlixBus' — that's `booking-service`'s question, against its own data"*). If `booking-service` didn't persist the ticket at confirmation time, FR-3.6 ("view and download their e-ticket") could only ever be served by re-asking the provider, which may not even be possible after the fact — so this service persists the full ticket content once, at `ConfirmBooking`/`DownloadTicket` time, and never calls the provider again to serve it. |

## The One Deliberate Non-Refresh: Fare and Provider Identity Are Frozen, Not Kept Current

Every other "captured copy" pattern on this platform (`search-service`'s index,
`inventory-service`'s first-party `Trip` rows) is explicitly a **kept-current** copy — corrected
by the next upstream event. `booking-service`'s copy of a trip's fare and provider identity is the
opposite: captured once, at hold time, and never touched again for the lifetime of that
`Booking`, even if `inventory-service`'s own `FareSnapshot` changes an hour later. This is worth
calling out because it is the one place this service's data-ownership posture differs from the
platform's usual pattern, and the difference is load-bearing: a booking's price is a commitment
made at a point in time, not a live-tracked value.

## Payment and Refund State: Referenced, Never Owned

`booking-service` holds a `paymentReference` — an opaque pointer into `payment-service`'s own
ledger — and a `cancellationReason` that may imply a refund was requested. It never holds the
payment transaction's own status, gateway response, or the refund's own lifecycle
(`INITIATED`/`COMPLETED`/`FAILED`). This is the same reasoning `docs/architecture/database-ownership.md`
states generally: *"the decision of whether a refund is owed... that's a `booking-service`/
`operator-service` policy decision"* is a decision this service makes, but the refund's own
execution and status are `payment-service`'s data, not duplicated here even as a cache.

## Retention

**Bookings are never deleted, at any status.** FR-1.3's booking history requirement, and the
general expectation that a financial/travel record remains available for support, dispute
resolution, and the traveler's own reference, mean `Booking` rows persist indefinitely — no
retention policy prunes them, unlike `inventory-service`'s `SyncRecord` history, which has no
compliance reason to be kept forever. `SeatHold` rows, being transient by design, can be pruned on
a simple schedule once consumed, released, or expired — see `use-cases.md`'s "Sweep Stale Holds."

## Rebuildability

**Not rebuildable from an event log, unlike most read models on this platform.** `Booking` is not
a derived read model of anything — it is the original source of truth for its own data, created by
a direct client action (`Create Booking`) and mutated by direct calls/events this service itself
processes. If this service's database were lost, the bookings themselves would be unrecoverable
from any other service's data (`inventory-service` never learns which trips were booked;
`provider-integration-service` never persists reservations or bookings past a single round-trip —
`docs/services/provider-integration-service/boundaries.md`). This is precisely why
`docs/architecture/high-level-design.md` §6 treats the booking↔payment path as the one place on
this platform that needs saga/outbox-grade consistency rather than "eventually consistent, and
rebuildable if wrong" — there is no rebuilding a lost booking record from anywhere else.

## Explicitly Not Designed Here

Physical Postgres schema, the exact `SeatHold`-to-`Booking` uniqueness-constraint mechanism
(`domain-model.md`'s invariants), the transactional outbox's table shape and publishing mechanism
(`boundaries.md`'s "What's Deliberately Out of Scope"), and how long a `Ticket`'s binary content
should live in Postgres versus an object store — all implementation decisions made when this
service is actually built, not architecture decisions.
