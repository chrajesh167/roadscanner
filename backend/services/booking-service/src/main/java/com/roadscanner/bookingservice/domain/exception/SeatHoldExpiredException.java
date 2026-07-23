package com.roadscanner.bookingservice.domain.exception;

import com.roadscanner.bookingservice.domain.model.SeatHoldId;

/** Thrown by {@code Create Booking} when the referenced hold's {@code expiresAt} has already
 * passed — the local-expiry-check resolution to
 * {@code docs/services/booking-service/boundaries.md}'s "Known Gap: No Read-Only
 * Reservation-Status Check". Matches FR-3.4's documented failure path: "traveler must re-select
 * seats." */
public class SeatHoldExpiredException extends BookingServiceException {

    private final SeatHoldId seatHoldId;

    public SeatHoldExpiredException(SeatHoldId seatHoldId) {
        super("Seat hold expired: " + seatHoldId);
        this.seatHoldId = seatHoldId;
    }

    public SeatHoldId seatHoldId() {
        return seatHoldId;
    }
}
