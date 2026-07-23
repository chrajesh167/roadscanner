package com.roadscanner.bookingservice.domain.exception;

import com.roadscanner.bookingservice.domain.model.BookingId;

/** Thrown by {@code Get Ticket} for a booking that isn't yet {@code CONFIRMED}/{@code COMPLETED}
 * — "no ticket yet," not an error about the booking itself
 * (docs/services/booking-service/use-cases.md's "Get Ticket"). */
public class TicketNotAvailableException extends BookingServiceException {

    private final BookingId bookingId;

    public TicketNotAvailableException(BookingId bookingId) {
        super("No ticket available yet for booking: " + bookingId);
        this.bookingId = bookingId;
    }

    public BookingId bookingId() {
        return bookingId;
    }
}
