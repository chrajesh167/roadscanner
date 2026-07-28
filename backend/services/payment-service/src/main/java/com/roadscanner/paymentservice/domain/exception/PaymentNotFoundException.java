package com.roadscanner.paymentservice.domain.exception;

/** Thrown when a payment does not exist, or is not owned by the requester — mapped to 404, the
 * same enumeration-protection posture {@code booking-service} applies so a caller cannot
 * distinguish "not yours" from "doesn't exist" (docs/services/payment-service/boundaries.md). */
public class PaymentNotFoundException extends PaymentServiceException {

    private final String paymentId;

    public PaymentNotFoundException(String paymentId) {
        super("No such payment: " + paymentId);
        this.paymentId = paymentId;
    }

    public String paymentId() {
        return paymentId;
    }
}
