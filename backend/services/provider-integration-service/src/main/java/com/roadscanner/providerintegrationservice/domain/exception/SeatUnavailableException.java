package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;

/** A {@code BlockSeat} request named one or more seats the provider reports as no longer
 * available (already blocked/booked by another caller, or never existed on that trip). */
public class SeatUnavailableException extends ProviderIntegrationException {

    public SeatUnavailableException(String message, ProviderError error) {
        super(message, error);
    }
}
