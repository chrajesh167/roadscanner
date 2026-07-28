package com.roadscanner.paymentservice.domain.exception;

/** Thrown when a requested refund would push total refunded past the captured amount — the second
 * guard (after the idempotency key) that makes duplicate/over-refunds impossible
 * (docs/services/payment-service/domain-model.md's invariants). Mapped to 409. */
public class RefundAmountExceededException extends PaymentServiceException {

    public RefundAmountExceededException(String paymentId) {
        super("Requested refund exceeds the captured amount for payment " + paymentId);
    }
}
