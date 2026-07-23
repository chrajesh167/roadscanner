package com.roadscanner.bookingservice.domain.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * Reacts to {@code SeatReleased} (from {@code provider-integration-service} —
 * <strong>specified, not yet published</strong>, see
 * docs/services/booking-service/events-consumed.md). Covers a hold that expires before the
 * traveler ever reaches {@code payment-service} — no {@code PaymentFailed}/{@code PaymentTimedOut}
 * will ever arrive for a payment attempt that never started. Keyed by
 * {@code providerBlockReference}, not a booking id — this event is about a reservation, not
 * necessarily an existing booking (a released hold with no {@code Booking} yet is simply
 * discarded). Must treat a second delivery for an already-handled release as a no-op.
 */
public interface HandleSeatReleased {

    void handle(Command command);

    record Command(String providerBlockReference, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(providerBlockReference, "providerBlockReference must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
