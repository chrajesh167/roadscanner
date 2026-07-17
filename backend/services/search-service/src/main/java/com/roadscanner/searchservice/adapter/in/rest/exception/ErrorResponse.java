package com.roadscanner.searchservice.adapter.in.rest.exception;

import java.time.Instant;
import java.util.List;

/**
 * Stable, client-facing error shape — identical to {@code auth-service}'s {@code ErrorResponse}.
 * Never carries internal detail (stack traces, database error text); only a safe, generic
 * message plus enough context (correlationId, path) to correlate with server-side logs.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
