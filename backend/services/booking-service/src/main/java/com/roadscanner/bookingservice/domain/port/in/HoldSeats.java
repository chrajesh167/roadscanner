package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Re-validates the trip against {@code inventory-service}, then places a hold with
 * {@code provider-integration-service} and persists a local {@link
 * com.roadscanner.bookingservice.domain.model.SeatHold} — kept as a separate client-facing step
 * from {@code Create Booking}, deliberately, per FR-3.2's "temporary hold... during checkout"
 * (docs/services/booking-service/boundaries.md's "Why `Hold Seats` Is a Separate Client-Facing
 * Step").
 *
 * <p>Fails with {@code TripNotBookableException} for a trip with no {@code ProviderMapping} —
 * docs/services/booking-service/use-cases.md's "A Trip With No `ProviderMapping` Cannot Be
 * Held".
 */
public interface HoldSeats {

    Result hold(Command command);

    record Command(UUID travelerId, TripId tripId, List<String> seatNumbers) {
        public Command {
            Objects.requireNonNull(travelerId, "travelerId must not be null");
            Objects.requireNonNull(tripId, "tripId must not be null");
            if (seatNumbers == null || seatNumbers.isEmpty()) {
                throw new IllegalArgumentException("seatNumbers must not be empty");
            }
            seatNumbers = List.copyOf(seatNumbers);
        }
    }

    record Result(SeatHoldId seatHoldId, List<String> seatNumbers, Instant expiresAt) {
    }
}
