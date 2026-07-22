package com.roadscanner.inventoryservice.domain.exception;

/** Base of every exception this service raises — matching {@code search-service}'s
 * {@code SearchServiceException} / {@code auth-service}'s equivalent base, so
 * {@code GlobalExceptionHandler} has exactly one fallback mapping for any future subtype. */
public abstract class InventoryServiceException extends RuntimeException {

    protected InventoryServiceException(String message) {
        super(message);
    }
}
