package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.TripId;

import java.time.Instant;
import java.util.Objects;

/**
 * Reacts to {@code TripCancelled} (from {@code inventory-service}) — cascades cancellation to
 * every non-terminal booking against the trip. {@code CONFIRMED} bookings get a
 * <strong>full refund regardless of the normal cancellation-fee policy</strong>
 * (docs/architecture/booking-flow.md step 7's explicit business rule — the traveler didn't cause
 * this) and, like every post-confirmation cancellation, attempt no provider-side reversal
 * (docs/services/booking-service/boundaries.md's "Known Gap"). {@code PENDING_PAYMENT} bookings
 * have their hold released and are cancelled with no refund needed.
 */
public interface HandleTripCancelled {

    void handle(Command command);

    record Command(TripId tripId, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
