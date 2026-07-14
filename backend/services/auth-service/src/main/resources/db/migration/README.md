# Flyway Migrations

`V1__create_auth_tables.sql` creates `credentials`, `refresh_tokens`, and `password_reset_requests`
— see `docs/services/auth-service/database-design.md` for the conceptual model these implement.

Hibernate's `ddl-auto` is `validate`, never `update` (see `application.yml`) — Flyway is the only
thing ever allowed to change this service's schema, in every environment including local. Add
new migrations as `V2__...`, `V3__...`; never edit `V1` after it has run anywhere.

`RoleAssignment` (the fourth domain entity — see `docs/services/auth-service/database-design.md`)
has no table yet: there is no `RoleAssignmentRepository` domain port for a table to serve, since
`AssignRole` is a business use case, out of scope for this persistence-layer bootstrap. Add its
migration alongside that port and use case, not ahead of them.
