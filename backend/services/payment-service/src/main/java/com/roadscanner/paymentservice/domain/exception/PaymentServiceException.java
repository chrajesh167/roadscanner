package com.roadscanner.paymentservice.domain.exception;

/** Base of every exception this service raises — matching {@code booking-service}'s
 * {@code BookingServiceException} / {@code inventory-service}'s equivalent base, so
 * {@code GlobalExceptionHandler} has exactly one fallback mapping for any future subtype. */
public abstract class PaymentServiceException extends RuntimeException {

    protected PaymentServiceException(String message) {
        super(message);
    }

    protected PaymentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
