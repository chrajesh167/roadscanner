package com.roadscanner.inventoryservice.adapter.in.rest.exception;

import com.roadscanner.inventoryservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.inventoryservice.domain.exception.InventoryServiceException;
import com.roadscanner.inventoryservice.domain.exception.ProviderMappingNotFoundException;
import com.roadscanner.inventoryservice.domain.exception.SeatLayoutNotFoundException;
import com.roadscanner.inventoryservice.domain.exception.TripNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Single global exception-mapping layer, RFC 7807 ({@code application/problem+json}) throughout —
 * requested explicitly for this service, a deliberate deviation from {@code auth-service}/
 * {@code search-service}/{@code provider-integration-service}'s custom {@code ErrorResponse}
 * record (their contracts are frozen and unrelated to this one; RFC 7807 is this service's own
 * error shape, not a platform-wide change). Every response carries {@code type}, {@code title},
 * {@code status}, {@code detail}, {@code instance}, plus a {@code correlationId} extension member
 * for log correlation, matching every other service's use of the same MDC key.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI VALIDATION_TYPE = URI.create("https://roadscanner.example/problems/validation-error");
    private static final URI NOT_FOUND_TYPE = URI.create("https://roadscanner.example/problems/not-found");
    private static final URI INTERNAL_ERROR_TYPE = URI.create("https://roadscanner.example/problems/internal-error");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", safeMessage(fe.getDefaultMessage())))
                .toList();
        log.warn("Validation failed on {}: {}", request.getRequestURI(), fieldErrors);
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", VALIDATION_TYPE, request);
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", VALIDATION_TYPE, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing required parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());
        return problem(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), VALIDATION_TYPE, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Malformed request parameter '{}' on {}", ex.getName(), request.getRequestURI());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter: " + ex.getName(), VALIDATION_TYPE, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Malformed request value on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", VALIDATION_TYPE, request);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ProblemDetail handleTripNotFound(TripNotFoundException ex, HttpServletRequest request) {
        log.info("Trip not found on {}: {}", request.getRequestURI(), ex.tripId());
        return problem(HttpStatus.NOT_FOUND, "No such trip", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler(SeatLayoutNotFoundException.class)
    public ProblemDetail handleSeatLayoutNotFound(SeatLayoutNotFoundException ex, HttpServletRequest request) {
        log.info("Seat layout not found on {}: {}", request.getRequestURI(), ex.tripId());
        return problem(HttpStatus.NOT_FOUND, "No seat layout for this trip", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler(ProviderMappingNotFoundException.class)
    public ProblemDetail handleProviderMappingNotFound(ProviderMappingNotFoundException ex, HttpServletRequest request) {
        log.info("Provider mapping not found on {}: {}", request.getRequestURI(), ex.tripId());
        return problem(HttpStatus.NOT_FOUND, "No provider mapping for this trip", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler(InventoryServiceException.class)
    public ProblemDetail handleInventoryServiceException(InventoryServiceException ex, HttpServletRequest request) {
        // Fallback for any future subtype that hasn't been given a specific mapping yet.
        log.warn("Inventory service exception on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Request could not be completed", VALIDATION_TYPE, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.",
                INTERNAL_ERROR_TYPE, request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, URI type, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }

    private String safeMessage(String message) {
        return message != null ? message : "Invalid value";
    }
}
