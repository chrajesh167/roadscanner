package com.roadscanner.bookingservice.domain.exception;

/** Thrown when {@code provider-integration-service}'s {@code BlockSeat} reports the seat(s)
 * unavailable — the provider's own accept/reject is authoritative
 * (docs/architecture/seat-locking-flow.md), so this is a real business outcome, not an
 * infrastructure failure. */
public class SeatUnavailableException extends BookingServiceException {

    public SeatUnavailableException(String message) {
        super(message);
    }
}
