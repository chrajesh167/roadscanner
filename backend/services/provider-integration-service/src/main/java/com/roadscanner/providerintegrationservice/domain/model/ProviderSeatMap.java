package com.roadscanner.providerintegrationservice.domain.model;

import java.util.List;
import java.util.Objects;

/** The seat layout for one provider trip. Like {@link ProviderTrip}, never persisted here — it's
 * short-lived cached (see {@code ProviderCache}, ~30s TTL, per this service's caching spec) purely
 * as a read-through optimization, with the provider always the source of truth. */
public record ProviderSeatMap(String providerTripId, ProviderType providerType, List<ProviderSeat> seats) {

    public ProviderSeatMap {
        if (providerTripId == null || providerTripId.isBlank()) {
            throw new IllegalArgumentException("providerTripId must not be blank");
        }
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(seats, "seats must not be null");
        seats = List.copyOf(seats);
    }

    public long availableSeatCount() {
        return seats.stream().filter(ProviderSeat::isAvailable).count();
    }
}
