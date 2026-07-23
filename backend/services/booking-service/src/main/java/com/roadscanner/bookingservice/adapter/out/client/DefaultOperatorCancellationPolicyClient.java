package com.roadscanner.bookingservice.adapter.out.client;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.OperatorCancellationPolicyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The shipped {@link OperatorCancellationPolicyClient} adapter — {@code operator-service} does
 * not exist yet (docs/services/booking-service/boundaries.md's "Relationship to
 * `operator-service`": <em>"an implementation decision left open"</em>). Implements the
 * documented interim default: <strong>full refund eligibility, no fee</strong> — the safest
 * choice for the traveler while this dependency doesn't exist. Replace with a real call the
 * moment {@code operator-service} exists — {@link OperatorCancellationPolicyClient}'s shape does
 * not need to change.
 */
@Component
class DefaultOperatorCancellationPolicyClient implements OperatorCancellationPolicyClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultOperatorCancellationPolicyClient.class);

    @Override
    public CancellationPolicy getCancellationPolicy(TripId tripId) {
        log.info("No operator-service to consult for trip {}'s cancellation policy — defaulting to full "
                + "refund eligibility, no fee (documented interim behavior)", tripId);
        return new CancellationPolicy(true, BigDecimal.ZERO, null);
    }
}
