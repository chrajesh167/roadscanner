package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One trip as a provider reported it, normalized into RoadScanner's own vocabulary.
 *
 * <p>This is the canonical shape every provider's response becomes. A caller receives an identical
 * record whether the trip came from FlixBus, RedBus or a rail operator — which is what lets
 * search-service stay provider-agnostic while still federating provider results.
 *
 * <p><strong>The three identifier fields exist for later booking, not for search.</strong> A
 * provider needs its own references handed back to block a seat or confirm a booking, and the only
 * moment they are available is the search response. They are opaque here: this service stores and
 * returns them, and never parses them.
 *
 * @param providerTripId  the provider's id for this trip — its ride/journey reference
 * @param fromStationId   the provider's id for the boarding point, when it reports one. Optional
 *                        because not every provider models stations distinctly from cities
 * @param toStationId     the provider's id for the alighting point, when it reports one
 * @param seatsAvailable  as reported at search time; a hint, never a guarantee — seat state is
 *                        re-validated live at hold time
 */
public record ProviderTrip(String providerTripId, ProviderType providerType, String operatorName, String origin,
                            String destination, Instant departureTime, Instant arrivalTime, String busType,
                            FareAmount fare, int seatsAvailable, String fromStationId, String toStationId) {

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
        fromStationId = blankToNull(fromStationId);
        toStationId = blankToNull(toStationId);
    }

    public Optional<String> fromStationIdIfPresent() {
        return Optional.ofNullable(fromStationId);
    }

    public Optional<String> toStationIdIfPresent() {
        return Optional.ofNullable(toStationId);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
