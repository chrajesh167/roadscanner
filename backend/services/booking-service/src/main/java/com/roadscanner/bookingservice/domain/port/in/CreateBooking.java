package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.Contact;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;

import java.util.Objects;
import java.util.UUID;

/**
 * Looks up the referenced {@code SeatHold}, checks its {@code expiresAt} locally against the
 * current clock (the resolution to docs/services/booking-service/boundaries.md's "Known Gap: No
 * Read-Only Reservation-Status Check" — no fresh call to {@code provider-integration-service} is
 * made here), and, if still valid, creates a {@code Booking} in {@code PENDING_PAYMENT},
 * consuming the hold. A hold token becomes <strong>at most one</strong> booking
 * (docs/architecture/booking-flow.md's idempotency requirement).
 *
 * <p><strong>Passengers are no longer supplied here.</strong> They were bound to seats when the
 * hold was placed — the provider requires the occupant at block time — so taking them again would
 * invite a booking whose travellers disagree with the ones the seats are actually held for. They
 * are copied from the hold instead. What this step adds is the contact, which the provider needs
 * only at checkout: where the ticket is sent.
 */
public interface CreateBooking {

    Result create(Command command);

    record Command(UUID travelerId, SeatHoldId seatHoldId, Contact contact) {
        public Command {
            Objects.requireNonNull(travelerId, "travelerId must not be null");
            Objects.requireNonNull(seatHoldId, "seatHoldId must not be null");
            Objects.requireNonNull(contact, "contact must not be null");
        }
    }

    record Result(BookingId bookingId, BookingStatus status) {
    }
}
