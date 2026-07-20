package com.roadscanner.providerintegrationservice.domain.exception;

/** The provider has no such trip — an unknown or stale {@code providerTripId} passed to
 * {@code GetSeatMap} or {@code BlockSeat}. */
public class ProviderTripNotFoundException extends ProviderIntegrationException {

    private final String providerTripId;

    public ProviderTripNotFoundException(String providerTripId) {
        super("No such provider trip: " + providerTripId, null);
        this.providerTripId = providerTripId;
    }

    public String providerTripId() {
        return providerTripId;
    }
}
