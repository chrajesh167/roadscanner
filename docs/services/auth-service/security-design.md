# Auth Service — Security Design

This deepens `docs/architecture/authentication-flow.md` with the specific decisions `auth-service` itself must make, rather than restating what that document already covers platform-wide.

## JWT Strategy

**Asymmetric signing (RS256/ES256), not symmetric (HS256).** With asymmetric signing, `auth-service` alone holds the private signing key; every other service — `api-gateway`, downstream services — verifies tokens using only the corresponding public key. A symmetric scheme would require distributing the *same* signing secret to every service that verifies tokens, meaning a compromise of any one of them exposes the ability to forge tokens for the entire platform. With asymmetric signing, compromising a downstream service only exposes its ability to *verify*, never to *issue*.

**Key identification.** The signing key's identifier is embedded in the token header (a `kid`-style claim), so more than one valid public key can be supported at once during a rotation window — old tokens signed with a previous key remain verifiable until they naturally expire, while new tokens use the new key. Exact rotation cadence and key-storage mechanism are implementation decisions, not designed here — likely backed by a secrets manager given the AWS-based infrastructure (see `docs/infrastructure/terraform`), but that vendor choice is not committed in this document.

**Claims (conceptual).** Subject (user identifier), role, issued-at, expiry. No profile data is ever embedded in the token — putting profile fields in the JWT would silently violate the boundary with `user-service` (`responsibilities.md`) by making `auth-service` a de facto profile distributor.

## Refresh Token Lifecycle

1. Issued alongside the access token at login, as a long-lived, opaque credential.
2. Stored durably in Postgres as a **hash**, never the raw value (`database-design.md`).
3. On use (refresh), the token is validated, then **rotated**: a new refresh token is issued, and the used one is marked as replaced, forming a chain.
4. **Reuse detection.** If an already-rotated (no longer current) refresh token is presented again, this is treated as a signal of compromise — the entire token family (every token in that rotation chain) is revoked immediately, forcing re-authentication. A legitimate client only ever holds the *current* token in the chain; a second presentation of a superseded one means someone else has a copy.
5. Logout revokes the current token; Logout All Sessions revokes every active chain for the user.

**Trade-off — rotation cost vs. security.** Rotating on every use means every refresh call is a write, not just a read, and requires reliably invalidating the prior token before or atomically with issuing the new one (a race here would let both the legitimate client and an attacker use overlapping tokens). This cost is accepted because refresh tokens are long-lived and therefore the highest-value credential on the platform to protect against theft.

## Password Handling

Passwords are hashed with a modern adaptive algorithm (bcrypt/argon2-class), never encrypted (which implies reversibility) and never stored in plain text. The hashing cost factor is an operational tuning knob, expected to increase over time as hardware improves — not a one-time decision.

## RBAC Strategy

`auth-service` issues **coarse-grained roles only**: `TRAVELER`, `OPERATOR`, `ADMIN`, `SUPPORT`, as a token claim. This is a deliberate ceiling, not an oversight:

- Fine-grained, resource-level authorization ("does this operator own this specific route") is enforced by the service that owns the resource, using the role claim plus its own data — never by `auth-service`, which has no visibility into other services' domain data and must not become a synchronous dependency for every authorization check platform-wide.
- **Role assignment:** self-registration defaults to `TRAVELER`. `OPERATOR` is granted via the synchronous, admin-triggered role-elevation call described in `responsibilities.md` (following operator-onboarding approval). `ADMIN`/`SUPPORT` are provisioned operationally, not self-service.

## Defense in Depth: Gateway vs. Service

Restating the platform rule (`docs/architecture/authentication-flow.md`) as it applies to this service's output: `api-gateway` validates a token's signature and expiry (**authentication**) on every call; each downstream service independently checks the role claim against the specific action being performed (**authorization**). `auth-service` never assumes the gateway's authentication check is sufficient authorization for anything — it is one layer of several.

## Brute-Force / Abuse Protection

- **Primary throttling** (rate limiting by IP/identifier) happens at `api-gateway`, consistent with its platform-wide rate-limiting responsibility (`docs/architecture/service-boundaries.md`).
- **Account-level lockout** is `auth-service`'s own defense-in-depth layer on top: repeated failed login attempts against a specific identifier lock that account temporarily, independent of gateway-level throttling, so a distributed attempt (many IPs, one target account) is still caught.

## Enumeration Protection

Login failures and password-reset requests return an identical, generic outcome whether or not the identifier exists (see `api-contract.md`, `exception-strategy.md`) — an attacker cannot use either operation to discover which accounts are registered.

## Transport & Secrets

All traffic is TLS-only. Signing keys and any inter-service shared secrets are sourced from a secrets manager, never checked into configuration or source. No token — access or refresh, raw or hashed — is ever written to application logs (see `logging-observability.md`'s redaction policy).
