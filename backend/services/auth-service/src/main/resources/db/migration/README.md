# Flyway Migrations

Empty by design as of this bootstrap. Flyway is configured and enabled (see `application.yml`)
and will run cleanly against zero migrations — there is nothing to migrate yet because no
entities exist (see `docs/services/auth-service/database-design.md`).

The first real migration (`V1__create_credential_table.sql` or similar) is written when the
`Credential` entity is implemented — see `docs/services/auth-service/implementation-roadmap.md`
step 4. Hibernate's `ddl-auto` is set to `validate`, never `update` — Flyway is the only thing
ever allowed to change this service's schema, in every environment, including local.
