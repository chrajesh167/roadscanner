# Flyway Migrations

`V1__create_auth_tables.sql` creates `credentials`, `refresh_tokens`, and `password_reset_requests`
— see `docs/services/auth-service/database-design.md` for the conceptual model these implement.

Hibernate's `ddl-auto` is `validate`, never `update` (see `application.yml`) — Flyway is the only
thing ever allowed to change this service's schema, in every environment including local. Add
new migrations as `V2__...`, `V3__...`; never edit `V1` after it has run anywhere.

`V2__create_role_assignments.sql` adds the `role_assignments` table — added, as this file
originally prescribed, alongside the `RoleAssignmentRepository` port and the `AssignRole` use
case rather than ahead of them. It is append-only by design: a role change is a new row, never
an `UPDATE`, and a user's current role is their most recent row (see
`docs/services/auth-service/database-design.md`, "Role Assignment").
