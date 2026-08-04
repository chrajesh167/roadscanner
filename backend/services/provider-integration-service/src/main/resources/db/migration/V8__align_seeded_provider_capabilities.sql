-- Realigns the seeded capability lists with what each adapter actually implements.
--
-- Two capabilities are new: BOOKING_CANCELLATION and ORDER_DETAILS.
--
-- FlixBus loses SEAT_RELEASE and TICKET_DOWNLOAD. Its documented API publishes no cart-abandon
-- endpoint and no ticket-download endpoint, and the adapter refuses both rather than calling a URL
-- FlixBus never defined. A capability list is what callers route work by, so leaving those in place
-- would send bookings down a path that cannot succeed — an advertised capability that fails at call
-- time is worse than one that was never offered.
--
-- The base URL is corrected at the same time. The placeholder was a stand-in from before the API
-- was documented; the real host is now known. The row stays enabled=false: a real partner token
-- still has to be stored through the admin API before anything calls it.

UPDATE provider_configurations
SET capabilities = 'SEARCH,SEAT_MAP,SEAT_BLOCK,BOOKING_CONFIRMATION,BOOKING_CANCELLATION,ORDER_DETAILS,HEALTH_CHECK',
    base_url     = 'https://global.api.flixbus.com',
    updated_at   = now()
WHERE provider_type = 'FLIXBUS';

-- The mock is a complete in-memory provider with no external dependency, so it keeps everything.
UPDATE provider_configurations
SET capabilities = 'SEARCH,SEAT_MAP,SEAT_BLOCK,SEAT_RELEASE,BOOKING_CONFIRMATION,BOOKING_CANCELLATION,'
                   || 'ORDER_DETAILS,TICKET_DOWNLOAD,HEALTH_CHECK',
    updated_at   = now()
WHERE provider_type = 'MOCK';
