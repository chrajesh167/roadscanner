# Search Service — Data Ownership

## What This Service Owns

Exactly one database, per `docs/architecture/database-ownership.md`'s hard rule — but it is worth being explicit that **nothing in it is a source of truth for anything.** `search-service` owns the *index itself* (the physical storage of `SearchableTrip` projections and `RatingSnapshot`s — see `domain-model.md`) as an artifact, the same way a cache owns its entries: fully real, fully queryable, and fully disposable.

No other service ever reads or writes this database — consistent with `database-ownership.md`'s "no service reads or writes another service's database, directly or indirectly," applied here in the direction that matters most for a read model: nobody depends on this database being correct at any given instant, because nobody but `search-service` itself ever queries it.

## Why "Owning a Database" Doesn't Mean "Being Authoritative" Here

Every other service's database ownership implies "ask this service, and only this service, what's true." `search-service`'s database ownership implies the opposite: "this is a local, expendable copy — ask `operator-service`, `inventory-service`, or `review-service` if you need the truth." This is the read-model pattern from `database-ownership.md` in its purest form on this platform — no other Phase 1 service is *entirely* composed of derived data with zero authoritative fields of its own.

## Rebuildability

Because every field in the index traces back to a specific upstream event (`events-consumed.md`) or a specific cached upstream read (`boundaries.md`), the entire index can be discarded and reconstructed from scratch by replaying `operator-service`'s and `review-service`'s retained event history — see `use-cases.md`'s "Rebuild Index." This is the concrete payoff `database-ownership.md` promises for the read-model pattern: "the copy is always disposable and rebuildable from the event log." If this index were lost entirely (a bad migration, a corrupted volume), the fix is a replay, not a data-recovery incident — a meaningfully different operational posture than losing `auth-service`'s or `booking-service`'s database would be.

## The Cache-in-Front-of-a-Cache

The short-TTL Redis layer in front of the `inventory-service` availability call (`docs/architecture/high-level-design.md` §7, `boundaries.md`) is worth calling out as its own ownership layer: it is a cache of a value that is *itself* already a live read from another service's authoritative store, sitting in front of a database (the index) that is *itself* already a derived copy. Two layers of staleness tolerance stacked on top of each other sounds worse than it is — each layer tolerates staleness for a different reason (the index because trip shape changes slowly and denormalizing it avoids an N-way fan-out per query; the cache because availability changes constantly and a raw per-query live call would fail NFR-1 under load). Per `docs/architecture/high-level-design.md` §7: "Redis is always a derived, expendable copy. If it's flushed, the platform degrades in latency, not correctness" — that guarantee holds here exactly as it does everywhere else Redis is used on this platform.

## Retention

Unlike `auth-service`'s refresh tokens or `booking-service`'s bookings, there is no audit or compliance reason to retain historical index state — a `SearchableTrip` projection's only job is to reflect the *current* state of a trip. Superseded projections (a cancelled trip, an old fare) can be purged on a simple schedule with no retention policy to design; the durable history of "what a trip's fare used to be" lives in `operator-service`'s own database and event log, not here.

## Explicitly Not Designed Here

The specific storage technology (Postgres full-text search, a dedicated search engine, or otherwise), physical schema, indexing/ranking implementation details, and the Redis cache's exact key structure and TTL value are implementation decisions made when this service is built — consistent with how `docs/services/auth-service/database-design.md` and `docs/architecture/seat-locking-flow.md` each defer their own physical/implementation specifics.
