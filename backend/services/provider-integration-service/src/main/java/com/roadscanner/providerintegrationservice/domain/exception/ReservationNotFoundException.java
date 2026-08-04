package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

/**
 * No stored hold matches the block reference a caller presented.
 *
 * <p>Distinct from a provider-side failure: nothing was asked of the provider. Either the reference
 * is wrong, or the hold was never recorded — and confirming a booking against a hold this service
 * cannot describe would send the provider identifiers it never issued.
 */
public class ReservationNotFoundException extends ProviderIntegrationException {

    public ReservationNotFoundException(ProviderType providerType, String providerBlockReference) {
        super("No reservation is stored for block reference " + providerBlockReference,
                new ProviderError(providerType, "RESERVATION_NOT_FOUND",
                        "The seat hold could not be found; it may have expired", false));
    }
}
