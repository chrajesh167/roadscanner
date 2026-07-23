package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.BookingId;

import java.time.Instant;
import java.util.Objects;

/** Reacts to {@code PaymentTimedOut} — same effect as {@link HandlePaymentFailed}, distinct
 * reason ({@code PAYMENT_TIMED_OUT}) preserved for reconciliation
 * (docs/architecture/payment-flow.md). */
public interface HandlePaymentTimedOut {

    void handle(Command command);

    record Command(BookingId bookingId, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(bookingId, "bookingId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
