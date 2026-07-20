package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;

/**
 * The provider could not be reached or failed at the transport level — a timeout, connection
 * refusal, 5xx response, or a tripped Resilience4j circuit breaker's fallback (see
 * {@code FlixBusExceptionTranslator}, {@code FlixBusConfiguration}). Always {@code retryable}
 * per {@link ProviderError#retryable()}: this is exactly the class of failure a caller may
 * reasonably retry after backoff, unlike {@link ProviderAuthenticationException} or
 * {@link BookingFailedException}.
 */
public class ProviderUnavailableException extends ProviderIntegrationException {

    public ProviderUnavailableException(String message, ProviderError error) {
        super(message, error);
    }

    public ProviderUnavailableException(String message, ProviderError error, Throwable cause) {
        super(message, error, cause);
    }
}
