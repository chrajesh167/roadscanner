# Search Service — Overview

## Purpose

`search-service` is the platform's **read-only trip discovery surface** — it answers "what trips exist from A to B on date D, ranked and filtered how I asked." It owns no source-of-truth data at all; it owns a derived, disposable index built from other services' events, tuned specifically for the read-heavy, latency-sensitive query pattern searching requires (NFR-1: ~2s p95).

## Where It Sits

- Referenced in `docs/architecture/high-level-design.md` §3, §7, §12 and `docs/architecture/service-boundaries.md` — this directory goes one level deeper into this service's own design, without duplicating what those documents already establish.
- Sits behind `api-gateway` like every other client-facing service (`high-level-design.md` §2) — no client reaches it directly.
- Has **no Kafka events of its own** — see `events-published.md`. It is a consumer of other services' events (`events-consumed.md`) and, for one specific need (live seat availability), a synchronous caller of `inventory-service` — see `boundaries.md`.

## Bounded Context

**In:** trip search, ranking, filtering, sorting, and maintaining the derived index that backs all three.

**Out:** trips, seats, fares, or reviews as source-of-truth (owned by `operator-service`, `inventory-service`, and `review-service` respectively); seat-level/seat-map detail (`inventory-service` + `customer-web`, direct); anything write-side (booking, holds, payment — search never mutates platform state).

See `responsibilities.md` for the full breakdown and `boundaries.md` for the harder boundary calls (why two different data-freshness strategies coexist in the same service).

## Relationship to Other Services

`search-service` is a textbook **read-model aggregator** (`docs/architecture/database-ownership.md`'s "locally-owned read model" pattern): it duplicates a denormalized shape of `operator-service`'s trip/schedule/fare data and `review-service`'s rating data, kept current via Kafka events, and overlays live seat-count data from `inventory-service` via a short-TTL-cached synchronous call. None of this duplication is a correctness risk — the index is disposable and rebuildable from the event log at any time (`database-ownership.md`), and a stale search result is a UX retry at hold time, never a double-booking (`service-boundaries.md`).

## Design Principles Carried From the Platform Level

- Own database, no shared schema, no cross-service joins (`database-ownership.md`) — even though every byte in it is a copy, not a source of truth.
- Eventual consistency is the accepted norm here (`docs/requirements/non-functional-requirements.md` NFR-10) — this is not the strongly-consistent booking↔payment path.
- Stateless, horizontally scalable (NFR-3) — no request-scoped state in process memory; all durable state lives in the index and the Redis cache in front of it.
- Health, metrics, and OpenAPI exposed from the first deployable commit (NFR-15), same as every other service.

## Documents in This Directory

| Document | Covers |
|---|---|
| `responsibilities.md` | Explicit responsibilities, non-responsibilities |
| `boundaries.md` | Deeper reasoning on the harder boundary calls — dual freshness strategy, relationship to each upstream service |
| `domain-model.md` | The read-model concepts this service holds, and why they aren't aggregates in the DDD sense |
| `use-cases.md` | Client-facing and internal (event-driven) use cases |
| `sequence-diagrams.md` | Index maintenance and query flows |
| `data-ownership.md` | What this service's database actually holds, and why none of it is authoritative |
| `events-published.md` | (None — and why that's correct, not an oversight) |
| `events-consumed.md` | Events from `operator-service` and `review-service` that keep the index current |
| `api-summary.md` | Client-facing operation categories (not endpoint-level) |
