package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.TripId;

import java.math.BigDecimal;

/**
 * The cancellation-policy lookup {@code docs/architecture/booking-flow.md} step 6 describes as a
 * synchronous call to {@code operator-service} — which does not exist yet
 * (docs/services/booking-service/boundaries.md's "Relationship to `operator-service`": <em>"not
 * resolved here... an implementation decision left open"</em>).
 *
 * <p>The shipped adapter ({@code adapter.out.client.DefaultOperatorCancellationPolicyClient})
 * implements the documented interim default: <strong>full refund eligibility, no fee</strong> —
 * the safest choice for the traveler while this dependency doesn't exist, easily replaced with a
 * real {@code operator-service} call later without changing this port's shape.
 */
public interface OperatorCancellationPolicyClient {

    CancellationPolicy getCancellationPolicy(TripId tripId);

    record CancellationPolicy(boolean fullRefundEligible, BigDecimal feeAmount, String feeCurrency) {
    }
}
