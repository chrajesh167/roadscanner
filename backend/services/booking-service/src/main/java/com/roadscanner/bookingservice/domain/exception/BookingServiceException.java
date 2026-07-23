package com.roadscanner.bookingservice.domain.exception;

/** Base of every exception this service raises — matching {@code inventory-service}'s
 * {@code InventoryServiceException} / {@code search-service}'s equivalent base, so
 * {@code GlobalExceptionHandler} has exactly one fallback mapping for any future subtype. */
public abstract class BookingServiceException extends RuntimeException {

    protected BookingServiceException(String message) {
        super(message);
    }
}
