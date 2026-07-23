package com.roadscanner.bookingservice.domain.model;

/** Exactly the four states {@code docs/architecture/booking-flow.md}'s frozen state diagram
 * defines — see {@code docs/services/booking-service/booking-state-machine.md} for every valid
 * transition. */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
