-- Sprint 5B: make the mapping table's uniqueness rules real.
--
-- V2 created idx_provider_city and idx_provider_station as PLAIN indexes, and created no index at
-- all on (provider, location_id). The code has assumed uniqueness ever since regardless:
-- ProviderLocationMappingRepository.findByLocationAndProvider, findByProviderCityId and
-- findByProviderStationId all return Optional. With duplicates present those lookups do not fail —
-- they silently return whichever row the planner reached first, so which canonical location a
-- provider id resolves to could differ between two identical calls. That is the worst shape a
-- data-integrity bug can take: no error, no log line, just a translation layer that occasionally
-- disagrees with itself.
--
-- Until now nothing could create a duplicate, because mappings could only be inserted by hand.
-- Sprint 5B gives administrators a create/update API, so the assumption needs an enforcer.
--
-- Postgres treats NULLs as distinct in a unique index, so each of the two provider-id rules below
-- means "unique when present" with no partial-index trickery — the same property V2 already relies
-- on for location.google_place_id. A mapping that carries only a city id therefore does not
-- collide with every other station-less mapping for that provider.
--
-- If this migration fails, an environment already holds violating rows. That is the constraint
-- doing its job: Postgres names the index and prints the conflicting key, and the duplicates must
-- be resolved by hand before the platform can trust its own translation layer. V4's seed cannot
-- have produced any — its inserts are guarded by NOT EXISTS on (provider, location_id).

-- Rule 1: one canonical location + one provider => at most one mapping.
--
-- Also replaces the missing index for findByLocationAndProvider, which until now fell back to
-- idx_provider_location and filtered on provider afterwards.
CREATE UNIQUE INDEX uq_provider_location_mapping_location_provider
    ON provider_location_mapping (provider, location_id);

-- Rule 2: one provider city id belongs to exactly one canonical location.
--
-- Supersedes V2's non-unique idx_provider_city: a unique index serves the same (provider,
-- provider_city_id) lookup, so keeping both would mean maintaining two structures for one access
-- path.
DROP INDEX idx_provider_city;

CREATE UNIQUE INDEX uq_provider_location_mapping_city
    ON provider_location_mapping (provider, provider_city_id);

-- Rule 3: one provider station id belongs to exactly one canonical location.
DROP INDEX idx_provider_station;

CREATE UNIQUE INDEX uq_provider_location_mapping_station
    ON provider_location_mapping (provider, provider_station_id);

-- idx_provider_location (location_id) and idx_provider_name (provider) are deliberately kept.
-- Neither is redundant: the first serves findByLocation, which queries location_id alone and
-- cannot use an index whose leading column is provider; the second serves the admin console's
-- filter-by-provider listing.
