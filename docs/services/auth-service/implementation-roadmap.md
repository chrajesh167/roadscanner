# Auth Service — Implementation Roadmap

Sequenced build order for when implementation begins, and why this order — not a sprint plan or a ticket breakdown.

## Sequence

1. **Module bootstrap.** Maven module under `backend/services/auth-service`, wired against `platform-bom` and the shared libraries listed in `package-structure.md`. Pure build-tooling, no business logic yet.
2. **Domain layer.** Models and ports (`package-structure.md`'s `domain/`) — no framework, no database, no HTTP. This is deliberately first: the core business rules (password policy, token rotation logic, RBAC assignment) should be written and unit-tested in complete isolation before any infrastructure exists to distract from getting them right.
3. **Application layer.** Use-case implementations against the domain's ports, still framework-light. At this point the service's entire behavior is testable without Spring, Postgres, or Redis running.
4. **Persistence adapter.** First real Flyway migrations and repository implementations, backed by Testcontainers-driven integration tests (`testing-strategy.md`).
5. **Security adapter.** JWT signing/verification implementation and key-loading, per `security-design.md`.
6. **Cache adapter.** Redis-backed revocation cache.
7. **REST adapter.** Controllers, DTO mapping, request validation wiring, global exception handling (`validation-strategy.md`, `exception-strategy.md`).
8. **Observability wiring.** Health endpoint, Prometheus metrics, structured logging, OpenAPI generation (`logging-observability.md`).
9. **Security hardening pass.** Rate limiting integration with `api-gateway`, account lockout tuning, a deliberate review of every client-facing error message against the enumeration-protection rule.
10. **Contract publication.** Finalized OpenAPI spec published to `docs/api/`, superseding `api-contract.md`'s category-level description with the real, versioned contract.
11. **Gateway integration.** Routing rules added to `api-gateway`.

**Why this order:** business logic (steps 2–3) is built and fully tested before any infrastructure concern (steps 4–7) is wired in — this is the practical payoff of the hexagonal package structure, not just a diagram. By the time the REST layer exists, the behavior it's exposing has already been proven correct in isolation.

## Future Extensibility

Not designed now, but the architecture above does not preclude any of these:

- **Multi-factor authentication.** Adds a second factor at login; fits as an additional domain policy and use-case step without changing the service's boundary or its relationship to `user-service`.
- **Social login / SSO** (Google, Apple, etc.) and **passwordless** (magic link, OTP-first login). Both are additional registration/login *use cases* within the same bounded context — `auth-service` remains the sole authority on "how was this identity verified," regardless of how many methods it eventually supports.
- **Mutual TLS between services**, flagged as a future hardening step in `docs/architecture/authentication-flow.md` — would tighten the service-to-service trust model this document currently relies on network-boundary trust for.
- **Session management UI** ("view/revoke your active devices") — the refresh-token data model (`database-design.md`) already carries device metadata specifically so this doesn't require a schema change later.
- **New actor types for Phase 2+ verticals** (e.g., an Airline Operator role, per `docs/requirements/actors.md`'s future actors) — fit as new values in the same coarse-grained role claim, not a redesign of the RBAC approach.

None of these are scheduled. They're listed here so a future extension has an obvious, low-friction seam to attach to, rather than requiring a boundary or data-model change to accommodate.
