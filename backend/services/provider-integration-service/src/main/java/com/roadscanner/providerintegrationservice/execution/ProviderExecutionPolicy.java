package com.roadscanner.providerintegrationservice.execution;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.time.Duration;
import java.util.Objects;

/**
 * How hard to try one provider call: how long to wait, how many attempts, how to space them, and
 * whether repeating is safe at all.
 *
 * <p>Built from the {@link Provider} row, so {@code timeout_ms} and {@code retry_count} are
 * operational settings an admin changes through the registry API — not constants compiled into an
 * adapter. That is the whole reason those columns exist: a provider whose API is slow can be given
 * a longer timeout without lengthening it for every other provider sharing the code path.
 *
 * @param maxAttempts total attempts including the first, so a {@code retry_count} of 2 means three
 *                    attempts. Named for what it is to avoid the perennial off-by-one over whether
 *                    "retries" includes the original call.
 */
public record ProviderExecutionPolicy(ProviderType providerType, String operation, Duration timeout, int maxAttempts,
                                      BackoffStrategy backoff, RetryStrategy retryStrategy) {

    public ProviderExecutionPolicy {
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(backoff, "backoff must not be null");
        Objects.requireNonNull(retryStrategy, "retryStrategy must not be null");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    /** Retryable policy for an idempotent operation, taking its settings from the provider row. */
    public static ProviderExecutionPolicy from(Provider provider, String operation, BackoffStrategy backoff) {
        return new ProviderExecutionPolicy(provider.type(), operation,
                Duration.ofMillis(provider.timeoutMillis()),
                provider.retryCount() + 1,
                backoff,
                RetryStrategy.retryableFailuresOnly());
    }

    /**
     * Single-attempt policy for an operation that must never be repeated.
     *
     * <p>Seat blocking and booking confirmation are the cases: a timeout leaves us unable to tell
     * whether the provider processed the request, so retrying risks a second hold or a duplicate
     * booking. Timeout still applies — only the repetition is removed.
     */
    public static ProviderExecutionPolicy nonIdempotent(Provider provider, String operation) {
        return new ProviderExecutionPolicy(provider.type(), operation,
                Duration.ofMillis(provider.timeoutMillis()), 1, BackoffStrategy.none(), RetryStrategy.never());
    }

    public boolean allowsRetry() {
        return maxAttempts > 1;
    }
}
