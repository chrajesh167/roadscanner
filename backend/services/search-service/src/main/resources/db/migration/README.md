# Flyway Migrations

`V1__create_searchable_trips.sql` creates the single `searchable_trips` table — see
`docs/services/search-service/domain-model.md` and `data-ownership.md` for the conceptual model
this implements.

Hibernate's `ddl-auto` is `validate`, never `update` (see `application.yml`) — Flyway is the only
thing ever allowed to change this service's schema, in every environment including local. Add
new migrations as `V2__...`, `V3__...`; never edit `V1` after it has run anywhere.

Unlike `auth-service`'s schema, there are no foreign keys here at all — every row in this table
is a disposable, rebuildable copy (`docs/architecture/database-ownership.md`), and `trip_id`/
`operator_id` reference identities this service's database has no local knowledge of.
