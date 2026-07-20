package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single search result, already translated from the provider's own response shape into
 * RoadScanner's canonical model by that provider's mapper (e.g. {@code FlixBusMapper}). Never
 * persisted — this service owns no inventory/booking state (see
 * docs/services/provider-integration-service/data-ownership.md); every field here is returned to
 * the caller and forgotten.
 */
public record ProviderTrip(String providerTripId, ProviderType providerType, String operatorName, String origin,
                            String destination, Instant departureTime, Instant arrivalTime, String busType,
                            FareAmount fare, int seatsAvailable) {

    public ProviderTrip {
        if (providerTripId == null || providerTripId.isBlank()) {
            throw new IllegalArgumentException("providerTripId must not be blank");
        }
        Objects.requireNonNull(providerType, "providerType must not be null");
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("operatorName must not be blank");
        }
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("origin must not be blank");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("arrivalTime must be after departureTime");
        }
        if (busType == null || busType.isBlank()) {
            throw new IllegalArgumentException("busType must not be blank");
        }
        Objects.requireNonNull(fare, "fare must not be null");
        if (seatsAvailable < 0) {
            throw new IllegalArgumentException("seatsAvailable must not be negative");
        }
    }
}
