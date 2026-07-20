-- Seed the two providers this service ships with. This is the entire "onboarding" step for a
-- provider whose adapter code already exists — see README.md "How to Add a New Provider".
--
-- MOCK ships enabled: it's a complete, self-contained adapter with no external dependency, used
-- for local development and end-to-end testing (docs/services/provider-integration-service/overview.md).
--
-- FLIXBUS ships disabled: the adapter is fully implemented and tested (FlixBusMapper's Javadoc
-- documents its contract), but RoadScanner has no real FlixBus base URL or credentials yet — see
-- README.md "Remaining Integration Points". Flipping it on is a config change (base_url,
-- credentials, enabled=true), not a code change.

INSERT INTO provider_configurations (id, provider_type, display_name, enabled, capabilities, base_url, created_at, updated_at)
VALUES (gen_random_uuid(), 'MOCK', 'Mock Provider', TRUE,
        'SEARCH,SEAT_MAP,SEAT_BLOCK,SEAT_RELEASE,BOOKING_CONFIRMATION,TICKET_DOWNLOAD,HEALTH_CHECK',
        NULL, now(), now());

INSERT INTO provider_configurations (id, provider_type, display_name, enabled, capabilities, base_url, created_at, updated_at)
VALUES (gen_random_uuid(), 'FLIXBUS', 'FlixBus', FALSE,
        'SEARCH,SEAT_MAP,SEAT_BLOCK,SEAT_RELEASE,BOOKING_CONFIRMATION,TICKET_DOWNLOAD,HEALTH_CHECK',
        'https://flixbus.example.invalid', now(), now());
