package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.BookingId;

import java.time.Instant;
import java.util.Objects;

/** Reacts to {@code PaymentFailed} — releases the hold (if not already released), transitions to
 * {@code CANCELLED} ({@code PAYMENT_FAILED}). No refund action — payment never succeeded. */
public interface HandlePaymentFailed {

    void handle(Command command);

    record Command(BookingId bookingId, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(bookingId, "bookingId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
