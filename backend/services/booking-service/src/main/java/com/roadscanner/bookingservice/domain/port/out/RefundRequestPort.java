package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.BookingId;

import java.math.BigDecimal;

/**
 * Requests a refund from {@code payment-service} — <strong>designed for, not yet real</strong>
 * (docs/services/booking-service/boundaries.md's "Relationship to `payment-service`").
 * {@code booking-service} decides <em>when</em> a refund is owed; it never decides or executes
 * the refund itself (docs/architecture/service-boundaries.md's {@code payment-service} entry).
 * The shipped adapter has no real target yet — see {@code adapter.out.client.NoOpRefundRequestAdapter}'s
 * Javadoc for the interim behavior.
 */
public interface RefundRequestPort {

    /** {@code amount = null} means "refund whatever was paid" (used for the trip-cancellation
     * full-refund case, where the fee schedule doesn't apply — docs/architecture/booking-flow.md
     * step 7); a non-null {@code amount} is used when a partial refund per policy applies. */
    void requestRefund(BookingId bookingId, String paymentReference, BigDecimal amount);
}
