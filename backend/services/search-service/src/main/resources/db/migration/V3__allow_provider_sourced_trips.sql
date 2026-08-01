-- Sprint 3A: provider-sourced trips have no RoadScanner operator.
--
-- V1 declared operator_id NOT NULL because every trip then came from operator-service, which only
-- ever knows first-party operators. inventory-service is now the merged, provider-agnostic catalog
-- (docs/services/search-service/events-consumed.md), and a trip it publishes from an external
-- provider has no first-party operator behind it — there is genuinely no id to store.
--
-- The alternative was fabricating a synthetic operator id to satisfy the constraint. That would
-- put a value in the read model resolving to no operator anywhere in the platform, and every
-- consumer would need to learn which ids are real. Making the column honest is cheaper and stays
-- correct as more providers are added.
--
-- Non-destructive: relaxing NOT NULL rewrites no rows and invalidates no existing data. Every row
-- written to date has an operator_id and keeps it.
ALTER TABLE searchable_trips
    ALTER COLUMN operator_id DROP NOT NULL;

-- operator_name stays NOT NULL: a provider-sourced trip still has a carrier name to display, it
-- simply is not a RoadScanner operator entity. That is the whole distinction being drawn here.
