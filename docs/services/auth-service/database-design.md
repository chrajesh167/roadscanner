# Auth Service — Database Design

This is a conceptual data model — entities, fields, and relationships in prose, not SQL. Physical schema and Flyway migrations are written at implementation start, not here (per `docs/architecture/database-ownership.md`, this database belongs to `auth-service` alone; no other service ever queries it directly).

## Entities

### Credential

The system of record for an identity's ability to authenticate. Conceptually holds:

- a user identifier — the canonical ID other services (notably `user-service`) reference for the same person
- a login identifier (email or phone) used at login
- a password hash (never the raw password — see `security-design.md`)
- account status (active / locked / suspended)
- timestamps: created, updated, last successful login

**Why last-login is stored:** it's the cheapest possible signal for both a security dashboard (dormant-account detection) and a support workflow ("when did this user last log in"), and costs nothing extra to maintain on every successful login.

### Role Assignment

A user's coarse-grained platform role. Modeled as its own concept rather than a column on `Credential` so that role history/audit (who changed a role, when) is a natural extension later without reshaping the credential record itself. Holds: user identifier, role (`TRAVELER` / `OPERATOR` / `ADMIN` / `SUPPORT`), assigned-at, assigned-by (which admin/service action granted it).

### Refresh Token (Session)

One row per issued refresh token, representing a session. Conceptually holds: a token identifier, a **hash of the token** (never the raw token — see rationale below), the owning user identifier, issued-at, expires-at, revoked-at (nullable), and a reference to the token it replaced (its predecessor in the rotation chain — see `security-design.md`'s reuse-detection design). Also holds minimal client/device metadata, to make a future "manage your active sessions" feature possible without a schema change.

**Why store only a hash of the refresh token, never the raw value:** identical reasoning to password hashing — if the database were ever compromised, a stored raw token is immediately usable by an attacker, while a hash requires the attacker to already possess the original token (which they'd only get by compromising the client, a much narrower attack).

### Password Reset Request

One row per issued reset attempt: a hash of the reset token (same rationale as above), the owning user identifier, expires-at, used-at (nullable — null means still usable, non-null makes it single-use).

## Relationships

- `Role Assignment` and `Refresh Token` both reference `Credential`'s user identifier — there is no foreign key to another service's database, since none exists; the "reference" is just a shared identifier value, resolved independently by whichever service needs to act on it.
- A `Credential` has many `Refresh Token` rows over its lifetime (one active chain per logged-in device/session, plus historical rotated/revoked ones retained for audit).

## Indexing Considerations (conceptual)

- Login identifier (email/phone) needs a unique lookup index — it's the hot path for every login attempt.
- Refresh tokens need an index on user identifier, to support "revoke all sessions for this user" (logout everywhere) and periodic cleanup of expired/revoked rows without a full table scan.

## Retention

Expired or revoked refresh tokens and used/expired password-reset requests don't need to live forever in the primary table — a periodic archival or cleanup pass is expected, but its schedule and mechanism are an implementation decision, not designed here.

## Redis vs. Postgres

Postgres is the **durable source of truth** for every entity above. Redis, per `docs/architecture/high-level-design.md` §7 ("Redis is always a derived, expendable copy"), holds a **fast-lookup revocation cache** built from the same data — writes (issuing or revoking a token) always land in Postgres first, then populate Redis, so the hot-path check ("is this refresh token still valid") can be answered from Redis without hitting Postgres on every request. If Redis were flushed, the platform would briefly fall back to checking Postgres directly — slower, but never incorrect, because Postgres remains authoritative.
