package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.RequesterContext;

import java.util.Objects;

/** Ownership-checked retrieval — see docs/services/booking-service/boundaries.md's "Booking ↔
 * Auth". Throws {@code BookingNotFoundException} both when the booking genuinely doesn't exist
 * and when the requester doesn't own it (enumeration protection). */
public interface GetBooking {

    Result get(Command command);

    record Command(BookingId bookingId, RequesterContext requester) {
        public Command {
            Objects.requireNonNull(bookingId, "bookingId must not be null");
            Objects.requireNonNull(requester, "requester must not be null");
        }
    }

    record Result(Booking booking) {
    }
}
