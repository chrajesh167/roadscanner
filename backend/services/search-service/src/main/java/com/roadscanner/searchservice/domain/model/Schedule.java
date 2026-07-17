package com.roadscanner.searchservice.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A trip's departure/arrival timing, as denormalized from {@code operator-service}'s
 * {@code TripPublished}/{@code TripUpdated} events (docs/services/search-service/domain-model.md).
 * Duration is derived, never carried separately, so it can never disagree with the two
 * timestamps it's computed from.
 */
public record Schedule(Instant departureTime, Instant arrivalTime) {

    public Schedule {
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("arrivalTime must be after departureTime");
        }
    }

    public Duration duration() {
        return Duration.between(departureTime, arrivalTime);
    }
}
