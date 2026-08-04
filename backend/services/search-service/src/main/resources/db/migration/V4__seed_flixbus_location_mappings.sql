-- Seeds the two FlixBus city mappings currently known, and the canonical RoadScanner locations
-- they attach to.
--
-- Two different kinds of data, deliberately in that order:
--
--   `location` is canonical platform reference data. Hyderabad and Bengaluru are real cities that
--   belong in the catalogue regardless of which providers serve them, and their ids are surrogate
--   keys this platform mints — not identifiers borrowed from anyone.
--
--   `provider_location_mapping` is the only table in the platform where a provider's own vocabulary
--   is allowed to appear. The two UUIDs below are FlixBus's real city identifiers.
--
-- Only these two are seeded because only these two are known. The rest are imported later from
-- FlixBus's own city list — deliberately not guessed, and deliberately not derived by matching city
-- names, because a mapping that is merely plausible sends a traveller to the wrong city and the
-- booking succeeds all the way to the ticket.
--
-- Fully idempotent: every insert is guarded by NOT EXISTS, so re-running against a database that
-- already has either half is a no-op rather than a duplicate. That matters because these rows may
-- also arrive through the location API before this migration ever runs.
--
-- No Java changes accompany this file, and none should. Onboarding the next provider is a provider
-- row, its credentials, and its mapping rows — nothing here is FlixBus-specific machinery.

-- 1. The canonical locations, created only where that city is not already catalogued. Matching on
--    city rather than display name is what prevents a second Hyderabad appearing under a different
--    label and splitting its trips across two ids that never match each other.
INSERT INTO location (id, display_name, city, state, country, active, created_at, updated_at)
SELECT gen_random_uuid(), seed.display_name, seed.city, seed.state, 'India', TRUE, now(), now()
FROM (VALUES
          ('Hyderabad', 'Hyderabad', 'Telangana'),
          ('Bengaluru', 'Bengaluru', 'Karnataka')
     ) AS seed(city, display_name, state)
WHERE NOT EXISTS (SELECT 1 FROM location existing WHERE existing.city = seed.city);

-- 2. The FlixBus mappings, attached to whichever location now represents each city.
--
--    Seeded verified = TRUE, against the column's default. The default exists because a mapping
--    produced by an automated import is a guess until someone checks it; these two were supplied
--    directly as known-good, which is exactly the human confirmation the flag records. Imported
--    mappings will still arrive unverified.
--
--    A city can hold several locations — the city itself plus individual stops such as MGBS — so
--    the row is chosen deterministically rather than by an unqualified join, which would otherwise
--    mint one mapping per stop and make the seed's result depend on table order. Preference goes to
--    the record whose display name is the city itself, then to the oldest, with id as a final
--    tie-break so the outcome is identical on every environment.
INSERT INTO provider_location_mapping
    (id, provider, location_id, provider_city_id, verified, last_synced, created_at, updated_at)
SELECT gen_random_uuid(), 'FLIXBUS', canonical.id, seed.provider_city_id, TRUE, now(), now(), now()
FROM (VALUES
          ('Hyderabad', '3da253ae-02ca-430c-87e5-22842065a77d'),
          ('Bengaluru', '2e46c6ce-d031-46f2-8ab5-e41038b8a029')
     ) AS seed(city, provider_city_id)
         JOIN LATERAL (
    SELECT l.id
    FROM location l
    WHERE l.city = seed.city
      AND l.active
    ORDER BY (l.display_name = seed.city) DESC, l.created_at, l.id
    LIMIT 1
    ) AS canonical ON TRUE
WHERE NOT EXISTS (SELECT 1
                  FROM provider_location_mapping mapped
                  WHERE mapped.provider = 'FLIXBUS'
                    AND mapped.location_id = canonical.id);
