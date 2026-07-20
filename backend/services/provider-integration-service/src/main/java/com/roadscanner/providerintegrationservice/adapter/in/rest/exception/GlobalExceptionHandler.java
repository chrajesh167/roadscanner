package com.roadscanner.providerintegrationservice.adapter.in.rest.exception;

import com.roadscanner.providerintegrationservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.providerintegrationservice.domain.exception.BookingFailedException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderAuthenticationException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderTripNotFoundException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.SessionExpiredException;
import com.roadscanner.providerintegrationservice.domain.exception.TicketNotFoundException;
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
 * Single global exception-mapping layer, matching {@code auth-service}/{@code search-service}'s
 * identical convention. Every {@link ProviderIntegrationException} subtype maps to the HTTP
 * status that best describes whose fault the failure is: {@link SessionExpiredException} → 401
 * (the caller must re-authenticate), {@link SeatUnavailableException}/{@link BookingFailedException}
 * → 409 (the requested state transition can't happen right now), {@link ProviderUnavailableException}
 * → 503 (the upstream provider, not this service, is down), {@link ProviderAuthenticationException}
 * → 502 (this service's own credentials with the provider are the problem, not the caller's request).
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
        log.warn("Malformed request parameter '{}' on {}", ex.getName(), request.getRequestURI());
        return respond(HttpStatus.BAD_REQUEST, "Invalid request parameter: " + ex.getName(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Malformed request value on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(ProviderNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleProviderNotSupported(ProviderNotSupportedException ex, HttpServletRequest request) {
        log.info("Provider not supported on {}: {}", request.getRequestURI(), ex.providerType());
        return respond(HttpStatus.NOT_FOUND, "No such provider, or it does not support the requested capability", request);
    }

    @ExceptionHandler(ProviderTripNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTripNotFound(ProviderTripNotFoundException ex, HttpServletRequest request) {
        log.info("Provider trip not found on {}: {}", request.getRequestURI(), ex.providerTripId());
        return respond(HttpStatus.NOT_FOUND, "No such trip", request);
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTicketNotFound(TicketNotFoundException ex, HttpServletRequest request) {
        log.info("Ticket not found on {}: {}", request.getRequestURI(), ex.bookingReference());
        return respond(HttpStatus.NOT_FOUND, "No ticket found for the given booking reference", request);
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSessionExpired(SessionExpiredException ex, HttpServletRequest request) {
        log.info("Session expired on {}: {}", request.getRequestURI(), ex.sessionId());
        return respond(HttpStatus.UNAUTHORIZED, "Provider session is no longer active; authenticate again", request);
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSeatUnavailable(SeatUnavailableException ex, HttpServletRequest request) {
        log.info("Seat unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.CONFLICT, "One or more requested seats are unavailable", request);
    }

    @ExceptionHandler(BookingFailedException.class)
    public ResponseEntity<ErrorResponse> handleBookingFailed(BookingFailedException ex, HttpServletRequest request) {
        log.info("Booking confirmation failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.CONFLICT, "The provider declined to confirm this booking", request);
    }

    @ExceptionHandler(ProviderAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleProviderAuthentication(ProviderAuthenticationException ex, HttpServletRequest request) {
        log.error("Provider authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_GATEWAY, "This service's credentials with the provider were rejected", request);
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderUnavailable(ProviderUnavailableException ex, HttpServletRequest request) {
        log.warn("Provider unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "The upstream provider is currently unavailable", request);
    }

    @ExceptionHandler(ProviderIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleProviderIntegration(ProviderIntegrationException ex, HttpServletRequest request) {
        // Fallback for any future subtype that hasn't been given a specific mapping yet.
        log.warn("Provider integration exception on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_GATEWAY, "The request could not be completed against the provider", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
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
