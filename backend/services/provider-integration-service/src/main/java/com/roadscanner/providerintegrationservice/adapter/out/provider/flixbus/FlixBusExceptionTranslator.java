package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.BookingFailedException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderAuthenticationException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderTripNotFoundException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.TicketNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;

/**
 * Maps every failure mode a {@code RestClient} call to FlixBus can produce — a non-2xx response
 * ({@link RestClientResponseException}), a timeout or connection failure
 * ({@link ResourceAccessException}), or anything else — into the canonical
 * {@link com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException}
 * hierarchy. Each {@code translate*} method interprets the same HTTP status differently depending
 * on which operation failed (a 404 means "no such trip" from the seat-map endpoint but "no such
 * ticket" from the ticket endpoint) — this context-dependent mapping is exactly why each
 * {@code FlixBus*Client} calls a dedicated method here rather than one generic translator.
 *
 * Every method other than {@link #translateHealthCheck} re-throws; {@code checkHealth} never
 * throws by contract (see {@code ProviderClient#checkHealth}), so its failures degrade to a
 * {@link HealthState#UNAVAILABLE} result instead.
 */
final class FlixBusExceptionTranslator {

    private static final Logger log = LoggerFactory.getLogger(FlixBusExceptionTranslator.class);

    private final Clock clock;

    FlixBusExceptionTranslator(Clock clock) {
        this.clock = clock;
    }

    RuntimeException translateAuthentication(RestClientException ex) {
        if (isStatus(ex, status -> status.value() == 401 || status.value() == 403)) {
            return new ProviderAuthenticationException("FlixBus rejected the authentication request",
                    error("AUTH_REJECTED", "Invalid or expired FlixBus credentials", false), ex);
        }
        return unavailable("authenticate", ex);
    }

    RuntimeException translateSearch(RestClientException ex) {
        return unavailable("search", ex);
    }

    RuntimeException translateSeatMap(RestClientException ex, String providerTripId) {
        if (isStatus(ex, status -> status.value() == 404)) {
            return new ProviderTripNotFoundException(providerTripId);
        }
        return unavailable("getSeatMap", ex);
    }

    RuntimeException translateBlock(RestClientException ex) {
        if (isStatus(ex, status -> status.value() == 409 || status.value() == 422)) {
            return new SeatUnavailableException("FlixBus reports one or more requested seats are unavailable",
                    error("SEAT_UNAVAILABLE", "Requested seat(s) are no longer available", false));
        }
        return unavailable("blockSeats", ex);
    }

    RuntimeException translateConfirm(RestClientException ex) {
        if (isStatus(ex, status -> status.value() == 409 || status.value() == 422 || status.value() == 410)) {
            return new BookingFailedException("FlixBus declined to confirm the booking",
                    error("BOOKING_DECLINED", "FlixBus declined the booking confirmation", false));
        }
        return unavailable("confirmBooking", ex);
    }

    RuntimeException translateTicket(RestClientException ex, BookingReference bookingReference) {
        if (isStatus(ex, status -> status.value() == 404)) {
            return new TicketNotFoundException(bookingReference);
        }
        return unavailable("downloadTicket", ex);
    }

    /**
     * The handler every {@code FlixBus*Client} method's Resilience4j {@code fallbackMethod}
     * delegates to (see {@code config.ResilienceConfig} / each client's {@code @CircuitBreaker}).
     * A circuit breaker's fallback intercepts <em>every</em> throwable that escapes the
     * protected call — including business outcomes like {@link SeatUnavailableException} that
     * were already correctly translated by the {@code translate*} methods above, not just
     * genuine infrastructure failures. Those must pass through unchanged: retrying or
     * circuit-breaking a legitimate "seat already taken" response would be wrong. Only a
     * throwable that isn't already one of this hierarchy's business exceptions — a
     * {@code CallNotPermittedException} (circuit open), a bulkhead/rate-limiter rejection, or
     * anything unexpected — is wrapped into {@link ProviderUnavailableException} here.
     */
    RuntimeException translateFallback(String operation, Throwable t) {
        if (t instanceof ProviderIntegrationException pie) {
            return pie;
        }
        log.warn("FlixBus {} failed after retry/circuit-breaker/bulkhead handling", operation, t);
        return new ProviderUnavailableException("FlixBus is unavailable during " + operation,
                error("PROVIDER_UNAVAILABLE", "FlixBus could not be reached or returned a server error", true),
                t instanceof Exception e ? e : new RuntimeException(t));
    }

    ProviderHealthCheck translateHealthCheck(RestClientException ex) {
        log.warn("FlixBus health check failed", ex);
        return new ProviderHealthCheck(ProviderType.FLIXBUS, HealthState.UNAVAILABLE, clock.instant(),
                "Health probe failed: " + ex.getMessage());
    }

    private ProviderUnavailableException unavailable(String operation, RestClientException ex) {
        log.warn("FlixBus call failed: {}", operation, ex);
        return new ProviderUnavailableException("FlixBus is unavailable during " + operation,
                error("PROVIDER_UNAVAILABLE", "FlixBus could not be reached or returned a server error", true), ex);
    }

    private boolean isStatus(RestClientException ex, java.util.function.Predicate<HttpStatusCode> predicate) {
        return ex instanceof RestClientResponseException responseException
                && predicate.test(responseException.getStatusCode());
    }

    private ProviderError error(String code, String message, boolean retryable) {
        return new ProviderError(ProviderType.FLIXBUS, code, message, retryable);
    }
}
