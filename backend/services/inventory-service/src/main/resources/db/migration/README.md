# Flyway Migrations

`V1__create_catalog_tables.sql` creates every table this service owns (`cities`, `stations`,
`routes`, `operator_refs`, `trips`, `seat_layouts` + `seat_layout_seats`, `provider_mappings`,
`sync_records`) — see `docs/services/inventory-service/domain-model.md` and `data-ownership.md`
for the conceptual model. `V2__seed_catalog_geography.sql` seeds cities/stations/routes, since
that catalog is administratively managed, not event-driven (`domain-model.md`'s summary table).

Hibernate's `ddl-auto` is `validate`, never `update` (see `application.yml`) — Flyway is the only
thing ever allowed to change this service's schema, in every environment including local. Add new
migrations as `V3__...`; never edit an already-applied migration.

`seat_layout_seats` has no status column, and never should — see `Seat`'s Javadoc and the request
that authorized this implementation: static shape only, live status lives entirely in
`provider-integration-service`.
