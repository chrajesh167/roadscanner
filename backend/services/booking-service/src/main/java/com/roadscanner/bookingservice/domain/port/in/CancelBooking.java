package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.RequesterContext;

import java.util.Objects;

/**
 * Traveler-initiated cancellation (FR-3.5). If {@code PENDING_PAYMENT}: releases the held
 * reservation, no refund needed. If {@code CONFIRMED}: checks cancellation policy (
 * {@code operator-service} dependency, not yet built — see
 * docs/services/booking-service/boundaries.md), requests a refund, and attempts
 * <strong>no provider-side reversal</strong> — the accepted interim policy for
 * docs/services/booking-service/boundaries.md's "Known Gap: Post-Confirmation Cancellation".
 * Idempotent — cancelling an already-{@code CANCELLED} booking is a no-op.
 */
public interface CancelBooking {

    Result cancel(Command command);

    record Command(BookingId bookingId, RequesterContext requester) {
        public Command {
            Objects.requireNonNull(bookingId, "bookingId must not be null");
            Objects.requireNonNull(requester, "requester must not be null");
        }
    }

    record Result(BookingStatus status) {
    }
}
