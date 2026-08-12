-- Record the provider's order reference, so a confirmed booking can actually be cancelled.
--
-- provider-integration-service's cancel route is keyed by the *order* reference (`MOCK-ORD-…`,
-- and each provider's equivalent). This service only ever stored the *booking* reference
-- (`MOCK-BK-…`) and sent that instead, so every explicit traveller cancellation of a confirmed
-- booking failed with a 404 from the provider, surfacing here as a 503. The two identifiers are
-- both minted by the provider at confirmation and neither can be derived from the other.
--
-- The value was already being returned: `ConfirmBooking`'s response carries `providerOrderReference`
-- and this service's client read it, then discarded it while mapping. This column is where it goes.
--
-- Nullable, and deliberately not backfilled. Rows confirmed before this migration genuinely have no
-- order reference recorded anywhere in this service, and inventing one — by copying the booking
-- reference, say — would send a value the provider will reject while looking like a real
-- cancellation attempt. Those bookings fail closed on cancellation, exactly as they do today, and
-- say why. Every booking confirmed from now on carries one.
--
-- No token column: the order token stays inside provider-integration-service, which resolves it
-- from its own `provider_bookings` row when asked to cancel by order reference. Copying a
-- credential-shaped value into a second service's database to save it a lookup would be a
-- needless second place for it to leak from.

ALTER TABLE bookings
    ADD COLUMN provider_order_reference VARCHAR(255);

COMMENT ON COLUMN bookings.provider_order_reference IS
    'The provider''s order identifier, captured at confirmation and required to cancel the order. '
        'NULL for bookings confirmed before this column existed, and for bookings that never '
        'reached the provider.';
