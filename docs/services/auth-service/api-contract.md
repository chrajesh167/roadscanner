# Auth Service — API Contract

This describes the service's public operations at the **contract level** — purpose, conceptual inputs/outputs, and behavioral guarantees. It deliberately stops short of HTTP verbs, paths, status codes, and field-level JSON schema — those are written as an OpenAPI spec (`docs/api/`) when implementation begins, per `docs/architecture/api-inventory.md`.

## Operations

| Operation | Purpose | Conceptual Input | Conceptual Output | Notes |
|---|---|---|---|---|
| Register | Create a new identity | Login identifier (email/phone), password | User identifier, access token, refresh token | Defaults role to `TRAVELER` |
| Login | Authenticate an existing identity | Login identifier, password | Access token, refresh token | Generic failure on bad identifier *or* bad password — see below |
| Refresh | Exchange a refresh token for a new access token | Refresh token | New access token, new (rotated) refresh token | Old refresh token invalidated on use — see `security-design.md` |
| Logout | End the current session | Refresh token | Confirmation | Revokes exactly one session |
| Logout All Sessions | End every session for the caller | (identity from access token) | Confirmation | Revokes the entire session set |
| Request Password Reset | Begin account recovery | Login identifier | Confirmation (always, regardless of whether identifier exists) | See enumeration-protection note below |
| Confirm Password Reset | Complete account recovery | Reset token, new password | Confirmation | Reset token is single-use |
| Assign Role *(internal)* | Elevate/change a user's platform role | User identifier, new role | Confirmation | Callable only by `operator-service`/`admin-console` per `responsibilities.md`, not client-facing |

## What's Deliberately Absent

**No "validate token" operation.** Access-token verification is stateless — `api-gateway` and every downstream service verify the JWT's signature locally using `auth-service`'s public key (see `security-design.md`), so there is nothing to call `auth-service` for on that hot path. This is a load-bearing design point: adding a "verify" endpoint later would reintroduce a synchronous dependency on `auth-service` for every single request platform-wide, defeating the point of using JWTs at all.

## Behavioral Contracts

- **Enumeration protection.** Login failure and Request Password Reset both return an identical, generic response regardless of whether the login identifier exists — the caller cannot distinguish "wrong password" from "no such account," and cannot use Request Password Reset to probe which identifiers are registered. This is a security requirement, not an oversight — see `security-design.md` and `exception-strategy.md`.
- **Refresh is not idempotent by design.** Each successful refresh call invalidates the refresh token used and issues a new one (rotation). A client retrying a refresh call with an already-used token will fail, by design — see `security-design.md`'s reuse-detection.
- **Confirm Password Reset is single-use.** A second attempt with the same reset token fails, regardless of whether the first attempt succeeded.
- **Registration failure on a taken identifier** is the one place identifier existence *is* revealed deliberately — a prospective new user needs to know their chosen email/phone is already registered so they can log in instead. This is intentionally asymmetric with the login/reset enumeration protection above: the risk profile differs (an attacker probing *existing* accounts vs. a legitimate user avoiding a duplicate signup).

## Consumers

Per `docs/architecture/api-inventory.md`: `customer-web`, `operator-portal`, and `admin-console` for the client-facing operations; `operator-service` (service-to-service) for Assign Role.
