package com.roadscanner.authservice.adapter.in.rest.exception;

import com.roadscanner.authservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.authservice.domain.exception.AuthServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Single global exception-mapping layer, per docs/services/auth-service/exception-strategy.md
 * and .claude/CODING_STANDARDS.md ("Global Exception Handling") — no scattered try/catch
 * elsewhere in the codebase.
 *
 * Two handlers exist today: structural validation failures (Bean Validation) and a generic
 * fallback. Business-specific exceptions (InvalidCredentialsException, TokenExpiredException,
 * etc., extending AuthServiceException) get their own @ExceptionHandler methods here as they
 * are introduced alongside their use cases — see implementation-roadmap.md. Every handler
 * added later must keep following the same rule already enforced below: never leak internal
 * detail to the client, and never let two different failure reasons (e.g. "unknown identifier"
 * vs "wrong password") be distinguishable from the response.
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

    @ExceptionHandler(AuthServiceException.class)
    public ResponseEntity<ErrorResponse> handleAuthServiceException(AuthServiceException ex, HttpServletRequest request) {
        // Generic fallback for the abstract base. Specific subtypes (once introduced) should
        // get their own handler mapping to the correct status (e.g. 401 for invalid
        // credentials, 423 for a locked account) rather than relying on this one.
        log.warn("Auth service exception on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(build(HttpStatus.BAD_REQUEST, "Request could not be completed", request, List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Never leak internal detail (stack trace, DB error text) to the client — full detail
        // goes to the log only, keyed by correlation id, per exception-strategy.md.
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(build(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SERVER_ERROR_MESSAGE, request, List.of()));
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
