package com.roadscanner.bookingservice.domain.model;

/** Set only when {@code status = CANCELLED}. This is where the requested {@code PAYMENT_FAILED}/
 * {@code EXPIRED} vocabulary lives — as a refinement of the terminal {@code CANCELLED} state, not
 * a state of its own (docs/services/booking-service/domain-model.md's "Reconciling the Requested
 * State Vocabulary"). */
public enum CancellationReason {
    PAYMENT_FAILED,
    PAYMENT_TIMED_OUT,
    HOLD_EXPIRED,
    TRAVELER_REQUESTED,
    TRIP_CANCELLED,
    PROVIDER_CONFIRMATION_FAILED
}
