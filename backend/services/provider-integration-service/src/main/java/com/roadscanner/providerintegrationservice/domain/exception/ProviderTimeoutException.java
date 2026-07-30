package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.time.Duration;

/**
 * A provider call exceeded the timeout configured for that provider.
 *
 * <p>Distinct from {@link ProviderUnavailableException} on purpose. "Refused the connection" and
 * "accepted it and then went quiet" call for different operational responses — the first points at
 * the provider being down, the second at it being overloaded or at our timeout being too tight for
 * the operation. Collapsing them into one type makes that impossible to tell from a dashboard.
 *
 * <p>Retryable: a timeout is by definition inconclusive. The request may or may not have been
 * processed, which is exactly why timeouts must never be retried on non-idempotent operations —
 * see {@code RetryStrategy}, which retries only where the caller declared it safe.
 */
public class ProviderTimeoutException extends ProviderIntegrationException {

    private final Duration timeout;

    public ProviderTimeoutException(ProviderType providerType, String operation, Duration timeout, Throwable cause) {
        super("Provider " + providerType + " timed out during " + operation + " after " + timeout.toMillis() + "ms",
                new ProviderError(providerType, "PROVIDER_TIMEOUT",
                        "The provider did not respond within the configured timeout", true),
                cause);
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}
