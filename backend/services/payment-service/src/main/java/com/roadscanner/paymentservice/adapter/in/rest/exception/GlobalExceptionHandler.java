package com.roadscanner.paymentservice.adapter.in.rest.exception;

import com.roadscanner.paymentservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.paymentservice.domain.exception.PaymentDeclinedException;
import com.roadscanner.paymentservice.domain.exception.PaymentGatewayException;
import com.roadscanner.paymentservice.domain.exception.PaymentNotFoundException;
import com.roadscanner.paymentservice.domain.exception.PaymentNotRefundableException;
import com.roadscanner.paymentservice.domain.exception.PaymentServiceException;
import com.roadscanner.paymentservice.domain.exception.RefundAmountExceededException;
import com.roadscanner.paymentservice.domain.exception.RefundNotFoundException;
import com.roadscanner.paymentservice.domain.exception.UnsupportedGatewayException;
import com.roadscanner.paymentservice.domain.exception.WebhookVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Single global exception-mapping layer, RFC 7807 ({@code application/problem+json}) throughout —
 * matching {@code inventory-service}'s and {@code booking-service}'s ProblemDetail convention. Every
 * response carries {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance},
 * plus a {@code correlationId} extension member. Never surfaces a raw gateway response, stack trace,
 * or instrument-adjacent detail to a client (NFR-12).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI VALIDATION_TYPE = URI.create("https://roadscanner.example/problems/validation-error");
    private static final URI NOT_FOUND_TYPE = URI.create("https://roadscanner.example/problems/not-found");
    private static final URI CONFLICT_TYPE = URI.create("https://roadscanner.example/problems/conflict");
    private static final URI FORBIDDEN_TYPE = URI.create("https://roadscanner.example/problems/forbidden");
    private static final URI DECLINED_TYPE = URI.create("https://roadscanner.example/problems/payment-declined");
    private static final URI UNAVAILABLE_TYPE = URI.create("https://roadscanner.example/problems/gateway-unavailable");
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

    @ExceptionHandler({ConstraintViolationException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("Malformed request on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", VALIDATION_TYPE, request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        log.info("Payment not found (or not owned) on {}: {}", request.getRequestURI(), ex.paymentId());
        return problem(HttpStatus.NOT_FOUND, "No such payment", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler(RefundNotFoundException.class)
    public ProblemDetail handleRefundNotFound(RefundNotFoundException ex, HttpServletRequest request) {
        log.info("Refund not found on {}: {}", request.getRequestURI(), ex.refundId());
        return problem(HttpStatus.NOT_FOUND, "No such refund", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler({PaymentNotRefundableException.class, RefundAmountExceededException.class})
    public ProblemDetail handleRefundConflict(PaymentServiceException ex, HttpServletRequest request) {
        log.info("Refund conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, ex.getMessage(), CONFLICT_TYPE, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Primarily the unique idempotency-key / one-active-payment-per-booking constraints
        // (V1__create_payment_tables.sql) — the database is the final arbiter when two concurrent
        // requests both pass the application-level idempotency check before either persists. The
        // losing request gets a clean 409 instead of a raw 500.
        log.info("Data integrity violation on {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "This request conflicts with an existing record", CONFLICT_TYPE, request);
    }

    @ExceptionHandler(WebhookVerificationException.class)
    public ProblemDetail handleWebhookVerification(WebhookVerificationException ex, HttpServletRequest request) {
        // 400, not 5xx — the gateway must not treat a rejected (unverifiable) webhook as retryable.
        log.warn("Webhook verification failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Webhook could not be verified", VALIDATION_TYPE, request);
    }

    @ExceptionHandler(UnsupportedGatewayException.class)
    public ProblemDetail handleUnsupportedGateway(UnsupportedGatewayException ex, HttpServletRequest request) {
        log.warn("Unsupported gateway on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Unsupported payment gateway", VALIDATION_TYPE, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.info("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Not authorized to perform this action", FORBIDDEN_TYPE, request);
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail handleDeclined(PaymentDeclinedException ex, HttpServletRequest request) {
        // A coarse, gateway-agnostic decline — never the gateway's raw reason if it could leak
        // instrument detail (NFR-12).
        log.info("Payment declined on {}: {}", request.getRequestURI(), ex.code());
        return problem(HttpStatus.PAYMENT_REQUIRED, "The payment was declined", DECLINED_TYPE, request);
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ProblemDetail handleGatewayException(PaymentGatewayException ex, HttpServletRequest request) {
        if (ex.retryable()) {
            log.warn("Gateway unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
            return problem(HttpStatus.SERVICE_UNAVAILABLE,
                    "The payment gateway is temporarily unavailable — please retry", UNAVAILABLE_TYPE, request);
        }
        log.info("Gateway rejected the request on {}: {}", request.getRequestURI(), ex.code());
        return problem(HttpStatus.PAYMENT_REQUIRED, "The payment could not be completed", DECLINED_TYPE, request);
    }

    @ExceptionHandler(PaymentServiceException.class)
    public ProblemDetail handlePaymentServiceException(PaymentServiceException ex, HttpServletRequest request) {
        log.warn("Payment service exception on {}: {}", request.getRequestURI(), ex.getMessage());
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
