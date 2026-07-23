package com.roadscanner.bookingservice.adapter.out.client;

import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.port.out.RefundRequestPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The shipped {@link RefundRequestPort} adapter — {@code payment-service} does not exist yet
 * (docs/services/booking-service/boundaries.md's "Relationship to `payment-service`"). Logs the
 * refund that would have been requested and returns, rather than throwing — a missing refund
 * target must never fail the booking-state transition that triggered it (the {@code Booking} is
 * already correctly {@code CANCELLED} by the time this is called; the refund is a downstream
 * consequence, not a precondition). Replace this adapter with a real HTTP client the moment
 * {@code payment-service} exists — {@link RefundRequestPort}'s shape does not need to change.
 */
@Component
class NoOpRefundRequestAdapter implements RefundRequestPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpRefundRequestAdapter.class);

    @Override
    public void requestRefund(BookingId bookingId, String paymentReference, BigDecimal amount) {
        log.warn("Refund requested for booking {} (paymentReference={}, amount={}) but payment-service "
                        + "does not exist yet — no-op. This refund must be actioned manually until "
                        + "payment-service is implemented.",
                bookingId, paymentReference, amount == null ? "FULL" : amount);
    }
}
