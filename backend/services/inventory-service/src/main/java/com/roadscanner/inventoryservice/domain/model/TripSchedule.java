package com.roadscanner.inventoryservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Departure/arrival for a {@link Trip} — a value object embedded in it
 * (docs/services/inventory-service/domain-model.md's summary table), not a separate aggregate:
 * modeled as its own type only so recurrence metadata has somewhere to live later, without
 * reshaping {@code Trip} — recurrence itself is not designed here, per that document. */
public record TripSchedule(Instant departureTime, Instant arrivalTime) {

    public TripSchedule {
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("arrivalTime must be after departureTime");
        }
    }
}
