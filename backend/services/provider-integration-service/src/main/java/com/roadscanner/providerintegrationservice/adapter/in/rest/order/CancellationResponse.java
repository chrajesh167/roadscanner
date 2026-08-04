package com.roadscanner.providerintegrationservice.adapter.in.rest.order;

import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The refunded amount is the provider's figure, not what the caller expected. Reporting an assumed
 * total here would hide fees and partial refunds until reconciliation.
 */
record CancellationResponse(String providerOrderReference, BigDecimal refundedAmount, String refundedCurrency,
                            Instant cancelledAt) {

    static CancellationResponse from(CancellationResult result) {
        return new CancellationResponse(result.providerOrderReference(), result.refundedAmount().amount(),
                result.refundedAmount().currency().getCurrencyCode(), result.cancelledAt());
    }
}
