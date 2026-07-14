# Auth Service — Testing Strategy

## Unit Tests — Domain Layer

No Spring context, no I/O, no Testcontainers. Covers: password hashing/verification policy behavior, token expiry calculation, password complexity policy, RBAC role-claim assignment logic, refresh-token rotation-chain logic. This layer's tests should run in milliseconds and form the bulk of the suite, per `package-structure.md`'s dependency direction — the domain has no reason to need infrastructure to be tested.

## Integration Tests — Adapter Layer

Against real Postgres and Redis via Testcontainers, using `backend/shared-libs/common-testing`'s shared setup: repository adapters (credential lookup, refresh-token persistence), the Redis-backed revocation cache, and the JWT signing/verification adapter (round-trip: sign, then verify, including rejecting a tampered token).

## Use-Case-Level Tests

Exercising a full use case (e.g., register → login → refresh → logout) within the service, wired against real adapters via Testcontainers — this is the layer that proves the service works end-to-end, without reaching into another service or across the network.

## Security-Specific Scenarios (Explicit, Non-Optional)

These are called out separately because they test the properties that matter most for this particular service, and are easy to silently skip if testing is left generic:

- Token reuse detection revokes the entire token family, not just the reused token.
- An expired access token is rejected.
- A tampered token (valid structure, invalid signature) is rejected.
- Login failure and password-reset-request return an identical outcome for "unknown identifier" vs. "wrong password" (enumeration protection — see `security-design.md`).
- Account lockout triggers after the defined threshold of failed attempts, and clears appropriately.
- A password-reset token cannot be used twice.
- A rotated refresh token cannot be reused after rotation (distinct from the reuse-*detection* test above — this one confirms the basic rotation invariant, the other confirms the compromise-response behavior).

## What's Out of Scope for This Service's Test Suite

Full platform end-to-end tests spanning `user-service`, `operator-service`, etc. — those belong at a platform/integration-test level once those services exist, not duplicated inside `auth-service`'s own suite. This service's tests validate its own contract and internal correctness, not the whole platform's behavior.

## Test Data

Synthetic fixtures only, provided via `common-testing` — no real user data, ever, in any test environment.

## Coverage Philosophy

Prioritize correctness of the security-critical paths listed above over chasing a coverage percentage. A service can hit an arbitrary coverage number while still missing the one test that actually matters (e.g., reuse detection) — the explicit scenario list above is the actual bar, not a numeric target.
