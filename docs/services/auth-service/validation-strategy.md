# Auth Service — Validation Strategy

## Two Layers, Deliberately Separate

**Structural (syntactic) validation** — cheap, no I/O, fails fast: is the login identifier a plausible email/phone shape, is a password present, is a required field missing. Enforced at `adapter/in/rest`, before any use-case runs.

**Semantic (business) validation** — may require I/O, runs inside the use-case in `application/`: is this identifier already registered, is this password reset token still valid and unused, does this password meet the platform's complexity policy.

**Why keep these separate:** cheap checks should never be delayed behind expensive ones. A malformed request is rejected before the service touches the database at all — this also reduces the surface for abuse (an attacker sending garbage input can't use it to generate load against Postgres).

## Password Policy

Enforced as a single domain policy object (`PasswordComplexityPolicy`, per `package-structure.md`), applied identically at registration and at password-reset confirmation. Centralizing it in the domain layer — rather than duplicating a validation rule in two separate adapter-level checks — means the policy can only ever drift by one deliberate change, not by two implementations quietly diverging over time.

## Uniqueness Checks Stay Within the Boundary

Whether a login identifier is already registered is checked against `auth-service`'s own store only. This never involves `user-service` — reinforcing `responsibilities.md`'s boundary: `auth-service` has no reason to know anything about `user-service`'s data to do its own job.

## Error Shape Philosophy

Validation failures (structural or business) are a distinct category from authentication failures (`exception-strategy.md` covers the full hierarchy): a malformed request is a client-input problem, while a wrong password is an identity problem. Keeping these conceptually distinct matters because they carry different security sensitivity — a validation error can safely say *what* was wrong (e.g., "password too short"); an authentication error must not (see the enumeration-protection rule in `security-design.md` and `api-contract.md`) reveal whether the identifier existed at all.

## What's Deliberately Not Specified Here

Exact complexity rules (minimum length, character-class requirements), exact regex/format validation for identifiers, and the specific bean-validation mechanism used are implementation decisions made when the service is built, not architecture decisions.
