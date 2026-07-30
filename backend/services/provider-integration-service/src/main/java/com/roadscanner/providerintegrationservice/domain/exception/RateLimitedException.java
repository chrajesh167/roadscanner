package com.roadscanner.providerintegrationservice.domain.exception;

import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.time.Duration;
import java.util.Optional;

/**
 * A provider rejected the call because we exceeded its rate limit.
 *
 * <p>Retryable, but not on the normal backoff schedule: when a provider tells us how long to wait
 * (via {@code Retry-After}), ignoring it and retrying on our own curve is the fastest way to get
 * throttled harder or blocked. {@link #retryAfter()} carries that instruction so the caller can
 * honour it.
 *
 * <p>Kept distinct from {@link ProviderUnavailableException} because the fix is different: an
 * unavailable provider is their problem, a rate-limited one is ours — our concurrency or our
 * request pattern.
 */
public class RateLimitedException extends ProviderIntegrationException {

    private final Duration retryAfter;

    public RateLimitedException(ProviderType providerType, String operation, Duration retryAfter, Throwable cause) {
        super("Provider " + providerType + " rate-limited the " + operation + " call",
                new ProviderError(providerType, "PROVIDER_RATE_LIMITED",
                        "The provider is rate limiting requests", true),
                cause);
        this.retryAfter = retryAfter;
    }

    /** How long the provider asked us to wait, when it said. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
