-- Adds the Hyderabad → Bengaluru route to catalog geography.
--
-- Catalog sync walks known routes and asks each provider what it serves on them, so a route that
-- does not exist here is never searched however well the provider is mapped. Both cities are
-- already seeded by V2 (Hyderabad 1111…106, Bengaluru 1111…103); only the route between them was
-- missing, which is why the demo route had no inventory trips while the five V2 routes did.
--
-- Fixed UUID rather than gen_random_uuid(), matching V2: catalog geography is administratively
-- managed reference data, and a referenceable, reproducible id is what lets tests and other
-- environments name this row. 33333333-…-306 continues V2's sequence.
--
-- Distance is the real road distance (~570 km), used for nothing but display and reporting; no
-- fare or duration is derived from it.
--
-- Idempotent: guarded by NOT EXISTS so re-running cannot duplicate the route. The reverse
-- direction is deliberately not added — nothing in the demo needs it, and an unused route would
-- cost a provider search per sync cycle forever.

INSERT INTO routes (id, origin_city_id, destination_city_id, distance_km)
SELECT '33333333-3333-3333-3333-333333333306',
       '11111111-1111-1111-1111-111111111106',
       '11111111-1111-1111-1111-111111111103',
       570.0
WHERE NOT EXISTS (SELECT 1 FROM routes existing WHERE existing.id = '33333333-3333-3333-3333-333333333306');
