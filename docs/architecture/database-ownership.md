# Database Ownership

## Rule

**Every service owns exactly one database. No service reads or writes another service's database, directly or indirectly (no shared connection pool, no cross-service joins, no service holding credentials to another's database.)**

This is a hard constraint, already stated in `.claude/ARCHITECTURE_RULES.md`, not a preference. This document explains why it's worth the cost, and how services get data they don't own without breaking it.

## Why

1. **Independent deployability.** If two services shared a schema, a migration for one could break the other, forcing coordinated releases — the exact thing microservices exist to avoid. Per-service ownership means `payment-service` can add a column tomorrow without a single other team knowing.
2. **Blast-radius containment.** A bad query, a lock contention issue, or a runaway migration in one service's database cannot degrade another service's availability. With 11 services and growing, this isolation is what keeps an incident local instead of platform-wide.
3. **Independent scaling and tuning.** `inventory-service`'s database sees very different load (high write contention on hot routes) than `analytics-service`'s (append-heavy, read-heavy for reporting). Separate ownership lets each be sized, indexed, and eventually even choose a different storage engine, independently.
4. **It's the only way service boundaries stay real.** A shared database is a shared implementation detail — it re-couples services that look independent at the API level. `database-ownership.md` and `service-boundaries.md` are two views of the same rule.

## How Services Get Data They Don't Own

Every cross-service data need falls into one of two patterns:

| Pattern | When to use | Example |
|---|---|---|
| **Synchronous API call to the owning service** | Low-volume, latency-tolerant, or must reflect the absolute latest state | `booking-service` calling `operator-service` to check a cancellation policy |
| **Locally-owned read model, kept current via Kafka events** | High-volume, latency-sensitive reads that can't afford a network hop per request | `search-service`'s index, built from `operator-service` and `inventory-service` events |

Neither pattern involves touching another service's database. The read-model pattern *does* duplicate data — that duplication is intentional and cheap to accept, because the copy is always disposable and rebuildable from the event log; the owning service's database remains the single source of truth.

## The Booking ↔ Payment Exception, Clarified

`booking-service` and `payment-service` coordinate tightly (see `booking-flow.md`, `payment-flow.md`) — but tight *application-level* coordination is not a database exception. Each still owns its own database. Consistency across the two is achieved through events and (eventually) a saga with a transactional outbox — see `high-level-design.md` §6 — never through a shared table or cross-service transaction.

## Migrations

Each service manages its own Flyway migration history, independently versioned. There is no platform-wide migration sequence to coordinate — a service's migrations are that service's concern alone, which is only true because no other service ever depends on its schema directly.

## Trade-off Being Accepted

This rule costs more upfront (every service needs its own schema, its own migration pipeline, sometimes duplicated reference data) and more ongoing engineering (read models to build and keep in sync) than one shared database would. It is accepted because the alternative — a shared schema — silently reintroduces a distributed monolith: 11 services that *look* independent in the service inventory but can't actually be deployed, scaled, or owned independently in practice.
