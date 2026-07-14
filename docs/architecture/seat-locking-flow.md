# Seat Locking Flow

## Problem

Multiple travelers can attempt to book the same seat on the same trip at the same moment — most acutely on popular routes close to departure, which is exactly when the system is under the most load. The design must guarantee **at most one successful hold per seat**, meet the sub-second latency target for the hold operation (NFR-2), and automatically release seats that are held but never converted into a booking (FR-3.4).

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

**Use Redis-based distributed locking as the primary mechanism for the seat hold itself**, because the "temporary hold" is a first-class product concept (FR-3.2), not an incidental race condition to smooth over — a pessimistic lock is the correct model for something the product explicitly wants to behave like a lock.

**Use optimistic locking (a version check) as a secondary safety net** at the single moment the hold is converted into a permanent reservation in `inventory-service`'s database of record (on `BookingConfirmed` — see `booking-flow.md`). Redis and Postgres are two different systems; a rare edge case (Redis failover timing, clock skew near TTL expiry) should never be allowed to result in an oversold seat in the durable record, even though such a case should already be prevented by the lock. This is belt-and-suspenders by design, not redundancy for its own sake — the two mechanisms protect two different systems.

## Mechanics (architecture level — not implementation)

- One Redis key conceptually represents one seat on one trip.
- Acquiring a hold is an **atomic "set if not exists" with a TTL** — atomicity is what guarantees only one of two simultaneous attempts can succeed; the TTL is what guarantees an abandoned hold self-releases.
- The hold token returned to the client references this lock and is what `booking-service` validates when creating the booking record (see `booking-flow.md`).
- TTL expiry releases the seat automatically. `inventory-service` may additionally emit an explicit `SeatReleased` event on expiry (e.g., driven by Redis keyspace notifications) for consumers that want it, rather than relying on them to infer expiry themselves — see `event-catalog.md`.
- Explicit release (traveler cancels checkout, or a cascaded `TripCancelled`) is a direct deletion of the same lock, not a separate mechanism.
- **The TTL must be set longer than the platform's maximum acceptable payment-processing time.** This isn't an arbitrary tuning knob — `booking-flow.md`'s asynchronous hold-to-reservation conversion is only safe *because* the hold is still guaranteed active when `PaymentCompleted` arrives. Getting this TTL wrong would silently break that guarantee.

## High-Concurrency / Hot-Route Considerations

Redis comfortably handles very high operation throughput, so many concurrent hold attempts on a single popular trip are not, by themselves, a scaling risk. The actual bottleneck risk on a hot route is search/availability *read* traffic (many more people looking than booking), which is addressed separately by the caching strategy in `high-level-design.md` §7 — seat locking and search caching are different problems with different solutions, not the same mechanism doing double duty.

## Failure Mode

If Redis is unavailable, checkout must **fail closed** — refuse new holds rather than fall back to "no locking," which would risk overselling. This is mitigated operationally (Redis high availability/replica failover), not by weakening the guarantee.

## Explicitly Not Designed Here

Exact Redis data structures, Lua script contents, specific TTL values, and the keyspace-notification implementation are implementation decisions made when `inventory-service` is built, not architecture decisions.
