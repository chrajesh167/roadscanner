package com.roadscanner.bookingservice.domain.exception;

/** Thrown by {@code Create Booking} when the submitted passenger list doesn't correspond
 * one-to-one with the held seats — "one passenger per held seat"
 * (docs/services/booking-service/responsibilities.md's "Booking validation"). */
public class PassengerSeatMismatchException extends BookingServiceException {

    public PassengerSeatMismatchException(String message) {
        super(message);
    }
}
