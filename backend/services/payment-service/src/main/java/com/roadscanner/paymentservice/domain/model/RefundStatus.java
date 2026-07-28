package com.roadscanner.paymentservice.domain.model;

/**
 * The {@link Refund} aggregate's own lifecycle, distinct from the {@link Payment} it refunds
 * (docs/services/payment-service/payment-state-machine.md's "Refund Sub-Lifecycle"). Maps onto
 * payment-flow.md's frozen {@code REFUND_INITIATED}/{@code REFUND_COMPLETED}/{@code REFUND_FAILED}.
 */
public enum RefundStatus {
    REQUESTED,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
