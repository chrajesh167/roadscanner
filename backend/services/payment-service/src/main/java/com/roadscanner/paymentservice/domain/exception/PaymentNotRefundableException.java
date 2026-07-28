package com.roadscanner.paymentservice.domain.exception;

/** Thrown when a refund is requested against a payment that was never captured — a refund is only
 * possible for a {@code CAPTURED} (or partially-refunded) payment
 * (docs/services/payment-service/payment-state-machine.md). Mapped to 409. */
public class PaymentNotRefundableException extends PaymentServiceException {

    public PaymentNotRefundableException(String paymentId, String status) {
        super("Payment " + paymentId + " is not refundable in status " + status);
    }
}
