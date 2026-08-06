package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.ProviderBooking;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.Optional;

/**
 * Stores confirmed provider orders and the handles needed to act on them afterwards.
 *
 * <p>The order token in particular cannot be re-requested from any provider: it is issued once,
 * with the order, and an order reference without its token cannot be cancelled. Losing a row here
 * turns a cancellable booking into a support ticket, which is why the write happens in the same
 * transaction that records the confirmation.
 */
public interface ProviderBookingRepository {

    ProviderBooking save(ProviderBooking booking);

    /** Looked up by the provider's own order reference, which is the handle a caller holds. */
    Optional<ProviderBooking> findByOrderReference(ProviderType providerType, String providerOrderReference);
}
