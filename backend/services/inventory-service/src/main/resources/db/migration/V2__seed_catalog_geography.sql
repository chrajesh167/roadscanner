-- Seed catalog geography — cities/stations/routes are administratively managed
-- (docs/services/inventory-service/domain-model.md's summary table: "kept current via:
-- administrative catalog-management, not event-driven"), so this is their only population
-- mechanism today. Explicit, fixed UUIDs (not gen_random_uuid()) so this seed data is
-- referenceable and reproducible across environments and tests — matching how reference/lookup
-- data is seeded, as distinct from user-generated rows elsewhere on this platform.
--
-- Routes chosen to match the demo routes already used in search-service's and
-- provider-integration-service's own READMEs/tests (Mumbai-Pune, Chennai-Bengaluru), so a fresh
-- environment is demonstrable end-to-end without inventing new routes no other service's docs
-- reference.

INSERT INTO cities (id, name, state, country) VALUES
    ('11111111-1111-1111-1111-111111111101', 'Mumbai', 'Maharashtra', 'India'),
    ('11111111-1111-1111-1111-111111111102', 'Pune', 'Maharashtra', 'India'),
    ('11111111-1111-1111-1111-111111111103', 'Bengaluru', 'Karnataka', 'India'),
    ('11111111-1111-1111-1111-111111111104', 'Chennai', 'Tamil Nadu', 'India'),
    ('11111111-1111-1111-1111-111111111105', 'Delhi', 'Delhi', 'India'),
    ('11111111-1111-1111-1111-111111111106', 'Hyderabad', 'Telangana', 'India');

INSERT INTO stations (id, city_id, name, type, latitude, longitude) VALUES
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'Mumbai Central Bus Stand', 'BUS_STAND', 18.9696, 72.8194),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111102', 'Pune Shivajinagar Bus Stand', 'BUS_STAND', 18.5308, 73.8474),
    ('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111103', 'Bengaluru Majestic Bus Terminal', 'TERMINAL', 12.9772, 77.5713),
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111104', 'Chennai Koyambedu Bus Terminal', 'TERMINAL', 13.0694, 80.1948),
    ('22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111105', 'Delhi Kashmere Gate ISBT', 'TERMINAL', 28.6667, 77.2280),
    ('22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111106', 'Hyderabad MGBS', 'BUS_STAND', 17.3753, 78.4744);

INSERT INTO routes (id, origin_city_id, destination_city_id, distance_km) VALUES
    ('33333333-3333-3333-3333-333333333301', '11111111-1111-1111-1111-111111111101', '11111111-1111-1111-1111-111111111102', 150.0),
    ('33333333-3333-3333-3333-333333333302', '11111111-1111-1111-1111-111111111102', '11111111-1111-1111-1111-111111111101', 150.0),
    ('33333333-3333-3333-3333-333333333303', '11111111-1111-1111-1111-111111111103', '11111111-1111-1111-1111-111111111104', 350.0),
    ('33333333-3333-3333-3333-333333333304', '11111111-1111-1111-1111-111111111104', '11111111-1111-1111-1111-111111111103', 350.0),
    ('33333333-3333-3333-3333-333333333305', '11111111-1111-1111-1111-111111111105', '11111111-1111-1111-1111-111111111106', 1500.0);
