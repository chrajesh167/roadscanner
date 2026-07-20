# Flyway Migrations

`V1`–`V4` create this service's four tables (`provider_configurations`, `provider_sessions`,
`audit_records`, `provider_health`) — see `docs/services/provider-integration-service/data-ownership.md`
for the conceptual model. `V5` seeds the two providers this service ships with (`MOCK` enabled,
`FLIXBUS` disabled pending real credentials) — see the top-of-file comment in `V5__seed_provider_configurations.sql`
and README.md "How to Add a New Provider" for why seeding a config row, not writing code, is how
a provider gets onboarded.

Hibernate's `ddl-auto` is `validate`, never `update` (see `application.yml`) — Flyway is the only
thing ever allowed to change this service's schema, in every environment including local. Add new
migrations as `V6__...`, `V7__...`; never edit an already-applied migration.

No foreign keys between these four tables and any other service's data — `provider_type` values
are this service's own concern (docs/architecture/database-ownership.md); `session_id` on
`audit_records` references this service's own `provider_sessions` table but isn't declared as a
formal foreign key, since an audit record for a since-deleted session (there is no delete path
today, but the column is nullable for exactly this future-proofing) must remain valid on its own.
