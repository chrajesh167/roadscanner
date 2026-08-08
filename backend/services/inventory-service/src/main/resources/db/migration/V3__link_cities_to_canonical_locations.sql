-- Sprint 7: give catalog geography a handle on the platform's canonical locations.
--
-- Catalog sync has to tell a provider which cities to search, in the provider's own vocabulary.
-- That translation lives in search-service's `provider_location_mapping`, the platform's single
-- mapping table, and it is keyed by a canonical `location.id`. This service had no way to name one:
-- its geography is keyed by its own `cities.id`, and nothing anywhere related the two. So sync sent
-- city *names* where providers require the ids they issued, and no trip could ever be synchronised
-- from a real provider.
--
-- This column is that missing relation, and nothing more. The mapping table is untouched and stays
-- owned by search-service; this only records which canonical location each catalog city *is*.
--
-- Deliberately nullable, and deliberately not populated here. Canonical location ids are minted per
-- environment (search-service's own seed uses gen_random_uuid()), so there is no correct literal to
-- write — and deriving one by matching city names is the failure mode the platform already refuses
-- by name: a merely plausible match sends a traveller to the wrong city and the booking succeeds
-- all the way to the ticket. Each row is set administratively, against the canonical id that
-- environment actually holds.
--
-- Until a city has one, sync skips every route through it and says so. That is the intended
-- behaviour, not a degraded one: importing a trip whose city could not be confirmed would put a
-- bookable seat in the catalog that nobody can vouch for.

ALTER TABLE cities
    ADD COLUMN location_id UUID;

COMMENT ON COLUMN cities.location_id IS
    'The canonical RoadScanner location (search-service `location.id`) this city is. Populated '
        'administratively; NULL means this city cannot be translated into any provider''s '
        'vocabulary and is skipped by catalog sync.';

-- One canonical location must not be claimed by two catalog cities: that would sync the same
-- provider trips onto two routes and duplicate them through the whole platform.
CREATE UNIQUE INDEX uq_cities_location_id ON cities (location_id) WHERE location_id IS NOT NULL;
