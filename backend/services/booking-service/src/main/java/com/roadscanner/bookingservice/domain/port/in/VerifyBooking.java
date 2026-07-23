package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.TripId;

import java.util.Objects;
import java.util.UUID;

/** Does a verified, {@code COMPLETED} booking exist for this traveler/trip pair — backs FR-7.2.
 * The only inbound service-to-service call any other service makes against
 * {@code booking-service} (docs/services/booking-service/boundaries.md's "Relationship to
 * `review-service`"). Consumed by {@code review-service} (not yet built). */
public interface VerifyBooking {

    Result verify(Command command);

    record Command(UUID travelerId, TripId tripId) {
        public Command {
            Objects.requireNonNull(travelerId, "travelerId must not be null");
            Objects.requireNonNull(tripId, "tripId must not be null");
        }
    }

    record Result(boolean verified) {
    }
}
