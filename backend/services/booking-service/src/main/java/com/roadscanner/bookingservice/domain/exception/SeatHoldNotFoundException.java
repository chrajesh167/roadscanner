package com.roadscanner.bookingservice.domain.exception;

import com.roadscanner.bookingservice.domain.model.SeatHoldId;

/** Thrown when a hold reference doesn't exist, doesn't belong to the requester, or was already
 * consumed by a prior {@code Create Booking} call — same enumeration-protection posture as
 * {@link BookingNotFoundException}. */
public class SeatHoldNotFoundException extends BookingServiceException {

    private final SeatHoldId seatHoldId;

    public SeatHoldNotFoundException(SeatHoldId seatHoldId) {
        super("No such seat hold: " + seatHoldId);
        this.seatHoldId = seatHoldId;
    }

    public SeatHoldId seatHoldId() {
        return seatHoldId;
    }
}
