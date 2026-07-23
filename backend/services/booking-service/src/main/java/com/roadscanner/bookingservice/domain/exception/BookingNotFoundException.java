package com.roadscanner.bookingservice.domain.exception;

import com.roadscanner.bookingservice.domain.model.BookingId;

/** Thrown both when a booking genuinely doesn't exist, and when it exists but the requester
 * doesn't own it — deliberately the same exception for both, mapped to the same {@code 404} in
 * {@code GlobalExceptionHandler}. This is the enumeration-protection posture
 * {@code docs/services/booking-service/boundaries.md}'s "Booking ↔ Auth" section requires: "a
 * traveler cannot distinguish 'not yours' from 'doesn't exist'." */
public class BookingNotFoundException extends BookingServiceException {

    private final BookingId bookingId;

    public BookingNotFoundException(BookingId bookingId) {
        super("No such booking: " + bookingId);
        this.bookingId = bookingId;
    }

    public BookingId bookingId() {
        return bookingId;
    }
}
