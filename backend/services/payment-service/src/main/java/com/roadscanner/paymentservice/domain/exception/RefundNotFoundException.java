package com.roadscanner.paymentservice.domain.exception;

/** Thrown when a refund does not exist for the referenced payment — mapped to 404. */
public class RefundNotFoundException extends PaymentServiceException {

    private final String refundId;

    public RefundNotFoundException(String refundId) {
        super("No such refund: " + refundId);
        this.refundId = refundId;
    }

    public String refundId() {
        return refundId;
    }
}
