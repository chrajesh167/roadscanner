# Auth Service — Responsibilities

## Responsibilities

- **Registration** — create a credential record for a new identity (identifier + password, or an OTP-based identity).
- **Login** — verify credentials, issue an access token and a refresh token on success.
- **JWT issuance** — sign short-lived access tokens carrying identity and role claims.
- **Refresh token lifecycle** — issue, rotate, and revoke refresh tokens (see `security-design.md`).
- **Session/token revocation** — logout (single session) and logout-everywhere (all sessions for a user).
- **Password reset** — issue and validate single-use reset tokens; enforce password policy on reset.
- **Coarse-grained role (RBAC) assignment** — assign and update a user's platform role (`TRAVELER`, `OPERATOR`, `ADMIN`, `SUPPORT`) as a token claim.
- **Health, metrics, OpenAPI exposure** — non-negotiable per `.claude/ARCHITECTURE_RULES.md`, same as every other service.

## Non-Responsibilities

- **Profile data.** Name, contact details, saved passengers — owned entirely by `user-service`. `auth-service` never stores or serves this data, and never calls `user-service` to fetch it either; it has no reason to.
- **Fine-grained / resource-level authorization.** Whether a specific operator may edit a specific route, or a specific support agent may view a specific booking, is a decision made by the service that owns that resource, using the role claim `auth-service` issued. `auth-service` provides the *class* of actor; it does not and cannot know every downstream authorization rule, and must not be turned into a bottleneck every other service calls to ask "is this allowed."
- **Notification delivery.** Password-reset emails/SMS are dispatched by `notification-service`, triggered by whatever mechanism `auth-service` uses to request one (see `api-contract.md`) — `auth-service` does not talk to an email/SMS provider itself.
- **User-facing profile UI or account settings beyond credentials.** That surface belongs to `user-service` + `customer-web`.

## Design Rationale for the Split

Restating and sharpening `service-boundaries.md`'s reasoning specifically for this service: identity/credential data carries a different security posture, audit obligation, and change cadence than profile data. A bug in "update saved passenger" must never be able to touch password hashes or refresh tokens. Keeping `auth-service` minimal — it *only* handles authentication primitives — keeps its attack surface and blast radius as small as possible, which matters disproportionately for this one service because everything else on the platform trusts what it issues.

## Boundary With User Service: Registration

Registration is **not** a single distributed transaction across `auth-service` and `user-service` — per `database-ownership.md`, there is no cross-service transaction on this platform. Two options were considered:

1. **`auth-service` synchronously calls `user-service`** to initialize a profile as part of registration.
2. **The client makes two calls** — register with `auth-service`, then create a profile with `user-service` — as one logical registration flow from the user's point of view, but two independently-owned writes.

**Decision: option 2, with `user-service` treating "no profile row found" as a default/empty profile rather than an error** when queried for a user ID it doesn't yet recognize. This avoids a synchronous dependency running in the *wrong* direction — `auth-service` is a foundational service other services depend on; it should not itself depend on `user-service` to complete its own core operation. It also avoids inventing a registration event purely to solve a problem the lazy-default approach solves for free.

**Trade-off accepted:** if the client's second call (profile creation) never happens, the user has valid credentials but a bare-minimum profile. This is acceptable — profile fields are supplementary, not required for authentication to function, and `user-service` can prompt to complete it on first real use.

## Boundary With Operator Service: Role Elevation

When `operator-service` approves an operator onboarding application (`user-journeys.md` Journey 4), the corresponding user's role must move from the registration default (`TRAVELER`) to `OPERATOR`. This is a rare, low-volume, admin-triggered action — a direct fit for `database-ownership.md`'s "synchronous API call to the owning service" pattern, not an event. `operator-service` (or `admin-console`, acting through `api-gateway` with an admin-scoped action) calls `auth-service`'s role-management capability directly. No event is introduced for this, consistent with `event-catalog.md` noting `auth-service` has no event surface.
