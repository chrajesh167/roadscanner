# Auth Service — Overview

## Purpose

`auth-service` is the platform's sole authority for **authentication** (who is this?) and **authorization primitives** (what class of actor are they?). It issues and manages the identity tokens every other service trusts. It does not manage who a user *is* beyond their credentials and role — that's `user-service`'s job.

## Where It Sits

- Referenced in `docs/architecture/high-level-design.md` §3 and §8, `docs/architecture/service-boundaries.md`, and `docs/architecture/authentication-flow.md` — this directory goes one level deeper, into the service's own design, without duplicating what those documents already establish.
- Sits behind `api-gateway` like every other service (`docs/architecture/high-level-design.md` §2) — no client reaches it directly.
- Has **no Kafka events**, in either direction (`docs/architecture/event-catalog.md`). Its interactions are synchronous request/response, because identity operations (login, refresh) are inherently in the client's critical, latency-sensitive path — there is nothing to usefully decouple asynchronously here.

## Bounded Context

**In:** credentials, login, JWT issuance, refresh-token lifecycle, session/token revocation, password reset, coarse-grained role (RBAC) assignment.

**Out:** profile data (name, contact, saved passengers — `user-service`), fine-grained/resource-level authorization decisions (owned by whichever service holds the resource, e.g. "does this operator own this route" is `operator-service`'s call to make, using the role claim `auth-service` issued).

See `responsibilities.md` for the full, explicit breakdown.

## Relationship to User Service

`auth-service` and `user-service` both key off the same user ID, but own entirely separate data (per `docs/architecture/database-ownership.md` — no shared database, no shared schema). `auth-service` is the identity's system of record for *authentication*; `user-service` is the system of record for *who that person is*. See `responsibilities.md` for how registration is coordinated across the boundary without a shared transaction.

## Design Principles Carried From the Platform Level

- Hexagonal architecture: domain logic isolated from HTTP/persistence/security-library concerns behind ports and adapters (`package-structure.md`).
- Own database, no shared schema (`database-design.md`).
- JWT-based authentication, gateway-authenticates/service-authorizes split (`security-design.md`).
- Health, metrics, and OpenAPI exposed from the first deployable commit (`logging-observability.md`).

## Documents in This Directory

| Document | Covers |
|---|---|
| `responsibilities.md` | Explicit responsibilities, non-responsibilities, rationale |
| `database-design.md` | Conceptual data model, ownership, Redis vs. Postgres split |
| `api-contract.md` | Operation-level API surface (categories, not endpoints) |
| `package-structure.md` | Hexagonal package layout, DTO responsibilities |
| `security-design.md` | JWT strategy, refresh token lifecycle, RBAC, key management |
| `validation-strategy.md` | Structural vs. business validation |
| `exception-strategy.md` | Exception hierarchy, security-safe error handling |
| `logging-observability.md` | Logging, metrics, audit trail |
| `testing-strategy.md` | Unit/integration test approach, security-specific test scenarios |
| `implementation-roadmap.md` | Build sequence, future extensibility |
