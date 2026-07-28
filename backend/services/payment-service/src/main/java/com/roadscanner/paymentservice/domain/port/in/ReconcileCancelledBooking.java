package com.roadscanner.paymentservice.domain.port.in;

import com.roadscanner.paymentservice.domain.model.BookingReference;

import java.time.Instant;
import java.util.Objects;

/**
 * {@code Reconcile Cancelled Booking} — consumes {@code BookingCancelled} for
 * <strong>reconciliation only</strong>, never as a refund initiator
 * (docs/services/payment-service/events-consumed.md, boundaries.md's "the Refund Trigger,
 * Reconciled"). Verifies that a refund-eligible cancellation whose payment was captured already has
 * a corresponding {@code Refund}; if none exists, records a discrepancy for support — never a blind
 * refund of an amount this service cannot compute.
 */
public interface ReconcileCancelledBooking {

    void reconcile(Command command);

    record Command(BookingReference bookingReference, String cancellationReason, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(bookingReference, "bookingReference must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
