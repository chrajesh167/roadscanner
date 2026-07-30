package com.roadscanner.providerintegrationservice.execution;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException;

/**
 * Decides whether a failed attempt is worth repeating.
 *
 * <p>Retrying the wrong failure is worse than not retrying at all: it multiplies load on a
 * provider that is already struggling, burns rate limit to reach an answer that cannot change, and
 * on a non-idempotent operation can double-book a seat. So the default implementation decides from
 * the failure's own declared retryability rather than from a list of status codes copied into each
 * adapter.
 */
@FunctionalInterface
public interface RetryStrategy {

    /**
     * @param attempt   the attempt that just failed, 1-based
     * @param maxAttempts total attempts permitted, including the first
     * @param failure   what it failed with
     */
    boolean shouldRetry(int attempt, int maxAttempts, Throwable failure);

    /**
     * The platform default: retry while attempts remain, and only failures the domain marked
     * retryable.
     *
     * <p>{@code ProviderError.retryable} is the single source of that judgement — set where the
     * provider's response is translated, which is the only place that knows what a given status
     * actually meant. A validation rejection and an unparseable body carry {@code retryable=false}
     * and stop immediately; a timeout or an unavailable provider carries true.
     *
     * <p>Anything that is not a {@link ProviderIntegrationException} — a bug in our own mapping
     * code, an {@code IllegalArgumentException} — is never retried. Repeating a programming error
     * just produces it again.
     */
    static RetryStrategy retryableFailuresOnly() {
        return (attempt, maxAttempts, failure) -> {
            if (attempt >= maxAttempts) {
                return false;
            }
            return failure instanceof ProviderIntegrationException providerFailure
                    && providerFailure.error() != null
                    && providerFailure.error().retryable();
        };
    }

    /** Never retries. For non-idempotent operations where a repeat could double-book. */
    static RetryStrategy never() {
        return (attempt, maxAttempts, failure) -> false;
    }
}
