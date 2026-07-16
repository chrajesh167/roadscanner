package com.roadscanner.authservice.adapter.in.rest.exception;

import com.roadscanner.authservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.authservice.domain.exception.AccountLockedException;
import com.roadscanner.authservice.domain.exception.AuthServiceException;
import com.roadscanner.authservice.domain.exception.IdentifierAlreadyRegisteredException;
import com.roadscanner.authservice.domain.exception.InvalidCredentialsException;
import com.roadscanner.authservice.domain.exception.PasswordPolicyViolationException;
import com.roadscanner.authservice.domain.exception.ResetTokenInvalidException;
import com.roadscanner.authservice.domain.exception.TokenExpiredException;
import com.roadscanner.authservice.domain.exception.TokenReusedException;
import com.roadscanner.authservice.domain.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
 * Every business handler follows two rules from exception-strategy.md: never leak internal
 * detail to the client, and never let enumeration-sensitive failure reasons be distinguishable
 * from the response. Concretely: token-expired and token-reused map to the identical 401 —
 * reuse detection is a server-side security signal (logged and audited here), not information
 * to hand the presenter of a stolen token. Password-policy and already-registered failures
 * deliberately DO carry a specific message, per validation-strategy.md ("a validation error
 * can safely say what was wrong") and api-contract.md's intentional registration asymmetry.
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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        // One message for unknown identifier, wrong password, and unknown refresh token alike
        // — the enumeration-protection rule, enforced at the final surface.
        log.warn("Authentication failure on {}", request.getRequestURI());
        return respond(HttpStatus.UNAUTHORIZED, "Invalid credentials", request);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        log.warn("Login rejected for non-active account {} on {}", ex.userId(), request.getRequestURI());
        return respond(HttpStatus.LOCKED, "Account is temporarily locked. Try again later.", request);
    }

    @ExceptionHandler({TokenExpiredException.class, TokenReusedException.class})
    public ResponseEntity<ErrorResponse> handleUnusableToken(AuthServiceException ex, HttpServletRequest request) {
        // Identical client response for expired vs reused: reuse detection is an audit/security
        // signal (logged below, tagged for the audit trail per logging-observability.md), never
        // information handed back to whoever presented the stolen token.
        if (ex instanceof TokenReusedException reused) {
            log.error("SECURITY: refresh token reuse detected (token row {}) on {}", reused.tokenId(), request.getRequestURI());
        } else {
            log.warn("Expired token presented on {}", request.getRequestURI());
        }
        return respond(HttpStatus.UNAUTHORIZED, "Token is invalid or expired", request);
    }

    @ExceptionHandler(ResetTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleResetTokenInvalid(ResetTokenInvalidException ex, HttpServletRequest request) {
        // One message for unknown, expired, and already-used — the client never learns which.
        log.warn("Invalid password-reset token presented on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Password reset token is invalid or expired", request);
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ErrorResponse> handlePasswordPolicy(PasswordPolicyViolationException ex, HttpServletRequest request) {
        // Safe to state which rule failed — no enumeration risk (validation-strategy.md).
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IdentifierAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleIdentifierTaken(IdentifierAlreadyRegisteredException ex, HttpServletRequest request) {
        // The one deliberate exception to enumeration protection — api-contract.md.
        log.info("Registration attempted with taken identifier on {}", request.getRequestURI());
        return respond(HttpStatus.CONFLICT, "This identifier is already registered", request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        // Internal/admin surface only — never thrown on client-facing operations.
        log.warn("Operation on unknown user {} via {}", ex.userId(), request.getRequestURI());
        return respond(HttpStatus.NOT_FOUND, "User not found", request);
    }

    @ExceptionHandler(AuthServiceException.class)
    public ResponseEntity<ErrorResponse> handleAuthServiceException(AuthServiceException ex, HttpServletRequest request) {
        // Fallback for any future subtype that hasn't been given a specific mapping yet.
        log.warn("Auth service exception on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Request could not be completed", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        // Domain value objects reject malformed input (bad identifier shape, bad UUID, unknown
        // role name) with IllegalArgumentException — a client-input problem, not a server error.
        // The raw message stays in the log; the client gets a stable generic one.
        log.warn("Malformed request value on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Uniqueness races (e.g. two concurrent registrations of the same identifier) surface
        // here after passing the advisory application-level check — the DB constraint is the
        // real guarantee. Never echo constraint/database text to the client.
        log.warn("Data integrity violation on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.CONFLICT, "Request conflicts with existing data", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        // Transient by definition (exception-strategy.md's retryable category): a concurrent
        // request updated the same row first — e.g. two refresh calls racing over one token.
        log.warn("Optimistic lock conflict on {}", request.getRequestURI());
        return respond(HttpStatus.CONFLICT, "Request conflicted with a concurrent update. Please retry.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Never leak internal detail (stack trace, DB error text) to the client — full detail
        // goes to the log only, keyed by correlation id, per exception-strategy.md.
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
