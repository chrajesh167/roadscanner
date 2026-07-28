package com.roadscanner.paymentservice.domain.exception;

/** A retryable gateway failure (timeout, 5xx) — mapped to 503 at the REST boundary. */
public class GatewayUnavailableException extends PaymentGatewayException {

    public GatewayUnavailableException(String message) {
        super("GATEWAY_UNAVAILABLE", message, true);
    }
}
