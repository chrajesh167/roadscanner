package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;

/** The provider declined a {@code ConfirmBooking} request (e.g. the block expired server-side
 * before confirmation, or the provider's own downstream validation rejected it). */
public class BookingFailedException extends ProviderIntegrationException {

    public BookingFailedException(String message, ProviderError error) {
        super(message, error);
    }
}
