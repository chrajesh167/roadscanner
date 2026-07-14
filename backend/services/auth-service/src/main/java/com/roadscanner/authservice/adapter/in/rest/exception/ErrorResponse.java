package com.roadscanner.authservice.adapter.in.rest.exception;

import java.time.Instant;
import java.util.List;

/**
 * Stable, client-facing error shape. Per
 * docs/services/auth-service/exception-strategy.md, this never carries internal detail
 * (stack traces, database error text) — only a safe, generic message plus enough context
 * (correlationId, path) to correlate with server-side logs.
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
