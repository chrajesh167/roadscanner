package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Looks up the referenced {@code SeatHold}, checks its {@code expiresAt} locally against the
 * current clock (the resolution to docs/services/booking-service/boundaries.md's "Known Gap: No
 * Read-Only Reservation-Status Check" — no fresh call to {@code provider-integration-service} is
 * made here), and, if still valid, creates a {@code Booking} in {@code PENDING_PAYMENT},
 * consuming the hold. A hold token becomes <strong>at most one</strong> booking
 * (docs/architecture/booking-flow.md's idempotency requirement).
 */
public interface CreateBooking {

    Result create(Command command);

    record Command(UUID travelerId, SeatHoldId seatHoldId, List<PassengerInput> passengers) {
        public Command {
            Objects.requireNonNull(travelerId, "travelerId must not be null");
            Objects.requireNonNull(seatHoldId, "seatHoldId must not be null");
            if (passengers == null || passengers.isEmpty()) {
                throw new IllegalArgumentException("passengers must not be empty");
            }
            passengers = List.copyOf(passengers);
        }
    }

    /** Deliberately field-for-field identical to {@code provider-integration-service}'s
     * {@code PassengerRequest} — see {@code domain.model.Passenger}'s Javadoc. */
    record PassengerInput(String fullName, int age, String gender, String seatNumber) {
    }

    record Result(BookingId bookingId, BookingStatus status) {
    }
}
