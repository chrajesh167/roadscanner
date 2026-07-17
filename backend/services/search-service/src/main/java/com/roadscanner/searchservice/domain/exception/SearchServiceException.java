package com.roadscanner.searchservice.domain.exception;

/**
 * Root of the search-service exception hierarchy, mirroring
 * {@code auth-service}'s {@code AuthServiceException} — a small root type with specific,
 * meaningfully-named subtypes for each distinct business failure. Search-service's failure
 * surface is deliberately narrow (it's a read-only aggregator with no write invariants to
 * violate), so this hierarchy has fewer subtypes than {@code auth-service}'s, not a smaller
 * commitment to the pattern.
 *
 * Framework-free by design: this is a domain type, not a Spring or HTTP type. Translation to an
 * HTTP response happens exclusively in
 * {@link com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler}.
 */
public abstract class SearchServiceException extends RuntimeException {

    protected SearchServiceException(String message) {
        super(message);
    }

    protected SearchServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
