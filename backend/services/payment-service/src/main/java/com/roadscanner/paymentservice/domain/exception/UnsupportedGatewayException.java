package com.roadscanner.paymentservice.domain.exception;

/** Thrown when no adapter is registered for the requested {@code GatewayType} — the payment
 * analogue of {@code provider-integration-service}'s {@code ProviderNotSupportedException}. */
public class UnsupportedGatewayException extends PaymentServiceException {

    public UnsupportedGatewayException(String gatewayType) {
        super("No payment gateway adapter registered for type: " + gatewayType);
    }
}
