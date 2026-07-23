package com.roadscanner.bookingservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** The canonical {@code inventory-service} trip id — this service never mints one, only carries
 * it, matching {@code inventory-service}'s own {@code TripId} shape
 * (docs/services/booking-service/domain-model.md). */
public record TripId(UUID value) {

    public TripId {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
