-- Seeds the MOCK provider's city mappings for the Hyderabad–Bengaluru demo route.
--
-- Federation discovers candidates from this table alone: a provider holding a city mapping for
-- *both* endpoints is asked, and one holding neither is not. MOCK held no mappings at all, so it
-- could never be selected for any route — which is why a search that reached provider-integration
-- fine still returned no provider trips. FLIXBUS rows are untouched; this only adds MOCK.
--
-- On MOCK's city identifiers. Every other provider's ids in this table are that provider's own,
-- copied from their registry and never guessed — V4's comment says exactly why. MOCK has no
-- registry: it is RoadScanner's in-memory stand-in, it accepts whatever city id it is handed and
-- echoes it back as the trip's origin/destination. So its identifier namespace is ours to define
-- rather than to discover, and these are defined, not derived: 'Hyderabad' and 'Bengaluru' are
-- chosen because a stand-in that reports readable endpoints keeps a demo honest, where an invented
-- code like MOCK-C1 would render as noise a viewer has to be told to ignore. This is deliberately
-- not the name-matching V4 refuses — nothing here matches a real provider's id by name, because
-- MOCK has none to match.
--
-- Idempotent in the same way as V4: every insert is guarded by NOT EXISTS, so re-running against a
-- database that already holds either half is a no-op.

-- 1. The canonical locations, in case this migration reaches an environment that has not run V4's
--    equivalent insert (fresh database, or one seeded through the location API instead). Matching
--    on city rather than display name keeps a second Hyderabad from appearing under another label
--    and splitting its mappings across two ids.
INSERT INTO location (id, display_name, city, state, country, active, created_at, updated_at)
SELECT gen_random_uuid(), seed.display_name, seed.city, seed.state, 'India', TRUE, now(), now()
FROM (VALUES
          ('Hyderabad', 'Hyderabad', 'Telangana'),
          ('Bengaluru', 'Bengaluru', 'Karnataka')
     ) AS seed(city, display_name, state)
WHERE NOT EXISTS (SELECT 1 FROM location existing WHERE existing.city = seed.city);

-- 2. The MOCK mappings, attached to whichever location represents each city in *this* environment.
--
--    Canonical location ids are minted per environment (gen_random_uuid() above), so there is no
--    literal to write and the row is resolved by join instead. The LATERAL ordering is V4's,
--    deliberately: prefer the record whose display name is the city itself, then the oldest, with
--    id as a final tie-break, so a city holding several locations (the city plus individual stops)
--    yields one mapping and the same one on every environment.
--
--    verified = TRUE: these were defined directly as known-good rather than produced by an
--    automated import, which is the human confirmation that flag records.
INSERT INTO provider_location_mapping
    (id, provider, location_id, provider_city_id, verified, last_synced, created_at, updated_at)
SELECT gen_random_uuid(), 'MOCK', canonical.id, seed.provider_city_id, TRUE, now(), now(), now()
FROM (VALUES
          ('Hyderabad', 'Hyderabad'),
          ('Bengaluru', 'Bengaluru')
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
                  WHERE mapped.provider = 'MOCK'
                    AND mapped.location_id = canonical.id);
