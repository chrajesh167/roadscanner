package com.roadscanner.bookingservice.adapter.in.rest.exception;

import com.roadscanner.bookingservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.bookingservice.domain.exception.BookingNotFoundException;
import com.roadscanner.bookingservice.domain.exception.BookingServiceException;
import com.roadscanner.bookingservice.domain.exception.PassengerSeatMismatchException;
import com.roadscanner.bookingservice.domain.exception.SeatHoldExpiredException;
import com.roadscanner.bookingservice.domain.exception.SeatHoldNotFoundException;
import com.roadscanner.bookingservice.domain.exception.SeatUnavailableException;
import com.roadscanner.bookingservice.domain.exception.TicketNotAvailableException;
import com.roadscanner.bookingservice.domain.exception.TripNotBookableException;
import com.roadscanner.bookingservice.domain.exception.UpstreamServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
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
 * matching {@code inventory-service}'s own deliberate deviation from the platform's more common
 * custom {@code ErrorResponse} record. Every response carries {@code type}, {@code title},
 * {@code status}, {@code detail}, {@code instance}, plus a {@code correlationId} extension member.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI VALIDATION_TYPE = URI.create("https://roadscanner.example/problems/validation-error");
    private static final URI NOT_FOUND_TYPE = URI.create("https://roadscanner.example/problems/not-found");
    private static final URI CONFLICT_TYPE = URI.create("https://roadscanner.example/problems/conflict");
    private static final URI FORBIDDEN_TYPE = URI.create("https://roadscanner.example/problems/forbidden");
    private static final URI UNAVAILABLE_TYPE = URI.create("https://roadscanner.example/problems/upstream-unavailable");
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

    @ExceptionHandler(PassengerSeatMismatchException.class)
    public ProblemDetail handlePassengerSeatMismatch(PassengerSeatMismatchException ex, HttpServletRequest request) {
        log.warn("Passenger/seat mismatch on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), VALIDATION_TYPE, request);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ProblemDetail handleBookingNotFound(BookingNotFoundException ex, HttpServletRequest request) {
        log.info("Booking not found (or not owned) on {}: {}", request.getRequestURI(), ex.bookingId());
        return problem(HttpStatus.NOT_FOUND, "No such booking", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler(SeatHoldNotFoundException.class)
    public ProblemDetail handleSeatHoldNotFound(SeatHoldNotFoundException ex, HttpServletRequest request) {
        log.info("Seat hold not found (or not owned) on {}: {}", request.getRequestURI(), ex.seatHoldId());
        return problem(HttpStatus.NOT_FOUND, "No such seat hold", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler(TicketNotAvailableException.class)
    public ProblemDetail handleTicketNotAvailable(TicketNotAvailableException ex, HttpServletRequest request) {
        log.info("Ticket not available on {}: {}", request.getRequestURI(), ex.bookingId());
        return problem(HttpStatus.NOT_FOUND, "No ticket available yet for this booking", NOT_FOUND_TYPE, request);
    }

    @ExceptionHandler(SeatHoldExpiredException.class)
    public ProblemDetail handleSeatHoldExpired(SeatHoldExpiredException ex, HttpServletRequest request) {
        log.info("Seat hold expired on {}: {}", request.getRequestURI(), ex.seatHoldId());
        return problem(HttpStatus.CONFLICT, "Seat hold expired — please re-select seats", CONFLICT_TYPE, request);
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ProblemDetail handleSeatUnavailable(SeatUnavailableException ex, HttpServletRequest request) {
        log.info("Seat unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, "One or more requested seats are unavailable", CONFLICT_TYPE, request);
    }

    @ExceptionHandler(TripNotBookableException.class)
    public ProblemDetail handleTripNotBookable(TripNotBookableException ex, HttpServletRequest request) {
        log.info("Trip not bookable on {}: {}", request.getRequestURI(), ex.tripId());
        return problem(HttpStatus.CONFLICT, "This trip cannot currently be booked", CONFLICT_TYPE, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.info("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Not authorized to perform this action", FORBIDDEN_TYPE, request);
    }

    @ExceptionHandler(UpstreamServiceUnavailableException.class)
    public ProblemDetail handleUpstreamUnavailable(UpstreamServiceUnavailableException ex, HttpServletRequest request) {
        log.warn("Upstream service unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is temporarily unavailable — please retry",
                UNAVAILABLE_TYPE, request);
    }

    @ExceptionHandler(BookingServiceException.class)
    public ProblemDetail handleBookingServiceException(BookingServiceException ex, HttpServletRequest request) {
        // Fallback for any future subtype that hasn't been given a specific mapping yet.
        log.warn("Booking service exception on {}: {}", request.getRequestURI(), ex.getMessage());
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
