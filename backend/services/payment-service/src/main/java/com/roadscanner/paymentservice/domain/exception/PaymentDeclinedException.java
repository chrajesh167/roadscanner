package com.roadscanner.paymentservice.domain.exception;

/** A non-retryable gateway decline (insufficient funds, invalid instrument, ...). */
public class PaymentDeclinedException extends PaymentGatewayException {

    public PaymentDeclinedException(String code, String message) {
        super(code, message, false);
    }
}
