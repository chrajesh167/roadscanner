package com.roadscanner.paymentservice.domain.model;

/**
 * The richer internal payment state vocabulary, mapped onto docs/architecture/payment-flow.md's
 * frozen coarse statuses at the cross-service boundary (docs/services/payment-service/payment-state-machine.md's
 * "Reconciling the Requested State Vocabulary"). The events crossing the service boundary always
 * use the frozen names, so {@code booking-service}'s frozen consumer contract is untouched.
 *
 * <pre>
 * CREATED -> PENDING -> AUTHORIZED -> CAPTURED
 * CREATED / PENDING / AUTHORIZED -> FAILED | CANCELLED | EXPIRED
 * CAPTURED -> REFUND_PENDING -> REFUNDED   (full refund; REFUND_PENDING -> CAPTURED on refund failure)
 * </pre>
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REFUND_PENDING,
    REFUNDED;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELLED || this == EXPIRED || this == REFUNDED;
    }

    /** Before any money moved — a payment in one of these states can still fail, cancel, or expire. */
    public boolean isPreCapture() {
        return this == CREATED || this == PENDING || this == AUTHORIZED;
    }
}
