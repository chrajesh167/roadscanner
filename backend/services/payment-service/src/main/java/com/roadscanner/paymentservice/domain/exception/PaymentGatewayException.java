package com.roadscanner.paymentservice.domain.exception;

/**
 * Base of every gateway-originated failure, translated at the adapter boundary from a
 * gateway-specific error into this canonical, gateway-agnostic hierarchy — exactly as
 * {@code provider-integration-service} translates provider errors
 * (docs/services/payment-service/domain-model.md's "Exception &amp; Error Translation"). Carries a
 * gateway-agnostic {@code code} and a {@code retryable} flag; application and REST code never see a
 * gateway-specific exception type.
 */
public class PaymentGatewayException extends PaymentServiceException {

    private final String code;
    private final boolean retryable;

    public PaymentGatewayException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
