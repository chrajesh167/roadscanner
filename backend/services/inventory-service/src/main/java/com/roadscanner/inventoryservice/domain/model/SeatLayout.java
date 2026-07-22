package com.roadscanner.inventoryservice.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The static seat-by-seat layout for one {@link Trip} — deck configuration and every {@link Seat}.
 * Materialized once (from {@code operator-service}'s layout snapshot embedded in
 * {@code TripPublished}, or from a provider's seat-map response during catalog synchronization)
 * and effectively immutable thereafter — a bus's physical configuration doesn't change per trip.
 *
 * Deliberately carries no live status of any kind — see {@link Seat}'s Javadoc. This is the
 * corrected replacement for what an earlier draft of this service's design called {@code SeatMap};
 * see docs/services/inventory-service/domain-model.md.
 */
public record SeatLayout(TripId tripId, List<Seat> seats) {

    public SeatLayout {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(seats, "seats must not be null");
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("seats must not be empty");
        }
        seats = List.copyOf(seats);
    }

    public int seatCount() {
        return seats.size();
    }
}
