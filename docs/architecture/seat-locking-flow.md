# Seat Locking Flow

> **Corrected by architecture review, 2026-07-22.** This document originally assigned seat
> locking to `inventory-service`, written before `provider-integration-service` existed.
> `provider-integration-service` now owns live seat availability, holds, and reservations
> (`docs/services/provider-integration-service/domain-model.md`,
> `docs/services/inventory-service/boundaries.md`). The mechanism reasoning below is unchanged and
> still correct — only the "which service" has moved, plus one new consideration (third-party
> providers) that didn't exist when this was first written.

## Problem

Multiple travelers can attempt to book the same seat on the same trip at the same moment — most acutely on popular routes close to departure, which is exactly when the system is under the most load. The design must guarantee **at most one successful hold per seat**, meet the sub-second latency target for the hold operation (NFR-2), and automatically release seats that are held but never converted into a booking (FR-3.4).

## Where This Guarantee Actually Lives Now

`provider-integration-service` aggregates trips from two kinds of supply
(`docs/services/inventory-service/overview.md` — "Two Supply Sources, One Catalog"), and the
correctness mechanism differs by which kind is being booked:

- **Third-party providers** (FlixBus, RedBus, AbhiBus, KSRTC, IntrCity, ...): the provider's own
  backend is what actually prevents two RoadScanner travelers (or a RoadScanner traveler and the
  provider's own direct customers) from double-booking the same seat. `provider-integration-service`'s
  `BlockSeat` call is a **relay**, not a lock — it asks the provider to block the seat and
  translates the provider's own accept/reject response
  (`SeatUnavailableException` if the provider says no). There is no RoadScanner-side lock to design
  for this case; the provider's own system of record is authoritative, full stop.
- **A future first-party/native supply path** (RoadScanner-hosted inventory with no external
  provider backing it, if one is ever built — not present today): this is the case the mechanism
  below was originally designed for, and remains the correct design *if and when* such a path
  exists. It is not designed today because no such path exists — `operator-service`'s first-party
  operators currently flow into `inventory-service`'s catalog only, with no live-booking mechanism
  of their own (see the open question this raises, in `docs/services/inventory-service/overview.md`'s
  scalability note).

Everything below describes the *pessimistic-lock* mechanism for the case where RoadScanner itself
is the system of record for a hold — i.e., the future first-party path, and, incidentally, the
internal request-level protection `provider-integration-service` may still want in front of a slow
third-party relay call (see "Applicability to Third-Party Relays" below).

## Two Approaches Considered

### Optimistic Locking (version-based, at the database layer)

Read the seat's current availability and version; attempt to update it `WHERE version = X`; a conflicting concurrent write causes the update to fail, and the loser retries or gives up.

- **Pros:** no lock held during "think time" (the gap between reading and writing), scales well when contention is low, simple failure mode.
- **Cons:** under high contention — many travelers hitting the *same* seat within the same second — most attempts fail and must retry, wasting work exactly when the system is busiest. Worse, the failure surfaces late: a traveler could fill in passenger details before discovering, at commit time, that the seat is gone.

### Distributed Locking (Redis-based, pessimistic)

Acquire an explicit, time-bound lock on a specific seat *before* allowing checkout to proceed. A second traveler is told the seat is unavailable immediately, not after racing to a failed commit.

- **Pros:** deterministic UX (rejected upfront, not after data entry), and it naturally *is* the business requirement — FR-3.2 asks for a temporary hold, and a TTL-based lock is literally that, not a technical workaround bolted onto something else. Expired holds clean themselves up via TTL with no separate cleanup job needed for the common case.
- **Cons:** requires discipline to never acquire a lock without a TTL (a lock that can leak forever is worse than no lock), and it puts Redis on the critical path for every hold attempt (mitigated by running Redis with high availability/replication, not a single instance).

## Decision

**Use Redis-based distributed locking as the primary mechanism for a RoadScanner-owned seat hold**, because the "temporary hold" is a first-class product concept (FR-3.2), not an incidental race condition to smooth over — a pessimistic lock is the correct model for something the product explicitly wants to behave like a lock. **This applies wherever `provider-integration-service` is itself the system of record for a hold** — today, that's not the common case (see above), but the mechanism remains the platform's answer for whenever it is.

**Use optimistic locking (a version check) as a secondary safety net** at the single moment a RoadScanner-owned hold is converted into a permanent reservation. Redis and Postgres are two different systems; a rare edge case (Redis failover timing, clock skew near TTL expiry) should never be allowed to result in an oversold seat in the durable record, even though such a case should already be prevented by the lock. This is belt-and-suspenders by design, not redundancy for its own sake — the two mechanisms protect two different systems.

## Applicability to Third-Party Relays

Even where the *authoritative* lock is the provider's own, `provider-integration-service` may still
choose to apply a short-lived, request-scoped guard (e.g., in front of `BlockSeat`) purely to avoid
issuing two near-simultaneous relay calls for the same seat from RoadScanner's own side — a
RoadScanner-internal optimization to reduce wasted/duplicate outbound calls, never a substitute for
the provider's own authority and never something a caller should rely on for correctness. Whether
this optimization is worth building is an implementation decision, not an architecture requirement
— the provider's own accept/reject response is what actually enforces the guarantee either way.

## Mechanics (architecture level — not implementation, for the RoadScanned-owned case)

- One Redis key conceptually represents one seat on one trip.
- Acquiring a hold is an **atomic "set if not exists" with a TTL** — atomicity is what guarantees only one of two simultaneous attempts can succeed; the TTL is what guarantees an abandoned hold self-releases.
- The hold token returned to the client references this lock and is what `booking-service` validates when creating the booking record (see `booking-flow.md`).
- TTL expiry releases the seat automatically. `provider-integration-service` may additionally emit an explicit `SeatReleased` event on expiry (e.g., driven by Redis keyspace notifications) for consumers that want it, rather than relying on them to infer expiry themselves — see `event-catalog.md`.
- Explicit release (traveler cancels checkout, or a cascaded `TripCancelled`) is a direct deletion of the same lock, not a separate mechanism.
- **The TTL must be set longer than the platform's maximum acceptable payment-processing time.** This isn't an arbitrary tuning knob — `booking-flow.md`'s asynchronous hold-to-reservation conversion is only safe *because* the hold is still guaranteed active when `PaymentCompleted` arrives. Getting this TTL wrong would silently break that guarantee.

## High-Concurrency / Hot-Route Considerations

Redis comfortably handles very high operation throughput, so many concurrent hold attempts on a single popular trip are not, by themselves, a scaling risk. The actual bottleneck risk on a hot route is search/availability *read* traffic (many more people looking than booking), which is addressed separately by the caching strategy in `high-level-design.md` §7 — seat locking and search caching are different problems with different solutions, not the same mechanism doing double duty.

## Failure Mode

For a RoadScanner-owned hold: if Redis is unavailable, checkout must **fail closed** — refuse new holds rather than fall back to "no locking," which would risk overselling. This is mitigated operationally (Redis high availability/replica failover), not by weakening the guarantee.

For a third-party-provider relay: if `provider-integration-service` cannot reach the provider, the
call fails with `ProviderUnavailableException`
(`docs/services/provider-integration-service/boundaries.md`) — `booking-service` treats this as a
failed hold attempt and reports it to the traveler; there is no local fallback lock to reach for,
because there was never a local lock providing the guarantee in the first place.

## Explicitly Not Designed Here

Exact Redis data structures, Lua script contents, specific TTL values, and the keyspace-notification implementation are implementation decisions made when this mechanism is actually built (for whichever service ends up owning a RoadScanner-side hold), not architecture decisions.
