package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.BookingId;

import java.time.Instant;
import java.util.Objects;

/**
 * Reacts to {@code PaymentCompleted} (from {@code payment-service}, not yet built) — calls
 * {@code provider-integration-service}'s {@code ConfirmBooking}, transitions to
 * {@code CONFIRMED} on success, or transitions to {@code CANCELLED}
 * ({@code PROVIDER_CONFIRMATION_FAILED}) plus a support flag and an automatic refund on failure
 * (docs/architecture/booking-flow.md's flagged edge case). Idempotent for an already-
 * {@code CONFIRMED} booking, and handles the late-success-after-cancellation edge case by
 * refunding plus flagging rather than re-confirming (docs/architecture/payment-flow.md).
 */
public interface HandlePaymentCompleted {

    void handle(Command command);

    record Command(BookingId bookingId, String paymentReference, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(bookingId, "bookingId must not be null");
            Objects.requireNonNull(paymentReference, "paymentReference must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
