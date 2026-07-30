package com.roadscanner.providerintegrationservice.execution;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with a cap and full jitter.
 *
 * <p>The jitter is the part that matters operationally. When a provider briefly fails, every
 * in-flight request fails at roughly the same moment; without jitter they all retry at exactly
 * {@code initial}, then all again at {@code initial × multiplier}, hammering a recovering provider
 * in synchronised waves and often re-breaking it. Randomising each delay across the whole interval
 * spreads the retries out.
 *
 * <p>The cap stops the delay growing without bound on a provider configured with a high retry
 * count — the point of retrying is to ride out a blip, not to hold a request thread for a minute.
 *
 * @param initial    delay after the first failure, before jitter
 * @param multiplier growth factor per subsequent attempt
 * @param max        upper bound on any single delay
 * @param jitter     whether to randomise; disabled in tests that assert exact delays
 */
public record ExponentialBackoffStrategy(Duration initial, double multiplier, Duration max, boolean jitter)
        implements BackoffStrategy {

    public ExponentialBackoffStrategy {
        if (initial == null || initial.isNegative()) {
            throw new IllegalArgumentException("initial backoff must not be negative");
        }
        if (max == null || max.isNegative()) {
            throw new IllegalArgumentException("max backoff must not be negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("backoff multiplier must be at least 1.0");
        }
        if (max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("max backoff must not be smaller than the initial backoff");
        }
    }

    @Override
    public Duration delayAfter(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }

        double scaled = initial.toMillis() * Math.pow(multiplier, attempt - 1D);
        long capped = (long) Math.min(scaled, max.toMillis());

        if (!jitter || capped == 0) {
            return Duration.ofMillis(capped);
        }
        // Full jitter: uniformly random in [0, capped]. Spreads a thundering herd rather than
        // merely shifting it.
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(capped + 1));
    }
}
