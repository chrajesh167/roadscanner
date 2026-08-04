package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * What a provider actually refunded when an order was cancelled.
 *
 * <p>The refunded amount is the provider's figure, not RoadScanner's expectation of it — deductions,
 * fees and partial refunds are the provider's to decide, and recording what we asked for instead of
 * what we were granted is how a reconciliation gap becomes invisible.
 */
public record CancellationResult(String providerOrderReference, FareAmount refundedAmount, Instant cancelledAt) {

    public CancellationResult {
        if (providerOrderReference == null || providerOrderReference.isBlank()) {
            throw new IllegalArgumentException("providerOrderReference must not be blank");
        }
        providerOrderReference = providerOrderReference.strip();
        Objects.requireNonNull(refundedAmount, "refundedAmount must not be null");
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
    }
}
