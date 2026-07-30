package com.roadscanner.providerintegrationservice.execution;

import java.time.Duration;

/**
 * Decides how long to wait before a retry.
 *
 * <p>Separate from {@link RetryStrategy} — <em>whether</em> to retry and <em>when</em> are
 * different decisions with different reasons to change. Whether depends on the failure's nature;
 * when depends on how much load the provider can take.
 */
@FunctionalInterface
public interface BackoffStrategy {

    /**
     * @param attempt the attempt that just failed, 1-based
     * @return how long to wait before the next attempt; never negative
     */
    Duration delayAfter(int attempt);

    /** No waiting. For tests that assert retry counts without spending real time. */
    static BackoffStrategy none() {
        return attempt -> Duration.ZERO;
    }
}
