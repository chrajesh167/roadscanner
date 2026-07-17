package com.roadscanner.searchservice.adapter.in.rest.exception;

import com.roadscanner.searchservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.searchservice.domain.exception.SearchServiceException;
import com.roadscanner.searchservice.domain.exception.TripNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/**
 * Single global exception-mapping layer, matching {@code auth-service}'s
 * {@code GlobalExceptionHandler} — one mapping layer, no scattered try/catch elsewhere in the
 * codebase. This service's failure surface is narrower (a read-only aggregator has no write
 * invariants to violate), so there are fewer handlers than {@code auth-service}'s, not a
 * smaller commitment to the same rule: never leak internal detail to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_SERVER_ERROR_MESSAGE = "An unexpected error occurred. Please try again.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), safeMessage(fe)))
                .toList();

        log.warn("Validation failed on {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(build(HttpStatus.BAD_REQUEST, "Validation failed", request, List.of()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing required parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());
        return respond(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        // A malformed path/query value that doesn't even parse into its target type (e.g. a
        // non-UUID tripId, an unparseable date) — a client-input problem, never a server error.
        log.warn("Malformed request parameter '{}' on {}", ex.getName(), request.getRequestURI());
        return respond(HttpStatus.BAD_REQUEST, "Invalid request parameter: " + ex.getName(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        // Domain value objects reject malformed input (e.g. origin == destination, an
        // out-of-range rating) with IllegalArgumentException — a client-input problem, not a
        // server error. The raw message stays in the log; the client gets a stable generic one.
        log.warn("Malformed request value on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTripNotFound(TripNotFoundException ex, HttpServletRequest request) {
        log.info("Trip not found on {}: {}", request.getRequestURI(), ex.tripId());
        return respond(HttpStatus.NOT_FOUND, "Trip not found", request);
    }

    @ExceptionHandler(SearchServiceException.class)
    public ResponseEntity<ErrorResponse> handleSearchServiceException(SearchServiceException ex, HttpServletRequest request) {
        // Fallback for any future subtype that hasn't been given a specific mapping yet.
        log.warn("Search service exception on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Request could not be completed", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Never leak internal detail (stack trace, DB/HTTP client error text) to the client —
        // full detail goes to the log only, keyed by correlation id.
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(build(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SERVER_ERROR_MESSAGE, request, List.of()));
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(build(status, message, request, List.of()));
    }

    private ErrorResponse build(HttpStatus status, String message, HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                fieldErrors
        );
    }

    private String safeMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value";
    }
}
