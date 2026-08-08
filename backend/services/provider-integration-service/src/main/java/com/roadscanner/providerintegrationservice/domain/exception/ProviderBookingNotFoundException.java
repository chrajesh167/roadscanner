package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

/**
 * No stored order matches the order reference a caller presented.
 *
 * <p>Distinct from a provider-side failure: nothing was asked of the provider. Without the stored
 * row this service does not hold the token that authorises acting on the order, and sending a
 * cancellation without one would be rejected far from the mistake that caused it.
 */
public class ProviderBookingNotFoundException extends ProviderIntegrationException {

    public ProviderBookingNotFoundException(ProviderType providerType, String providerOrderReference) {
        super("No provider booking is stored for order reference " + providerOrderReference,
                new ProviderError(providerType, "PROVIDER_BOOKING_NOT_FOUND",
                        "The provider order could not be found", false));
    }
}
