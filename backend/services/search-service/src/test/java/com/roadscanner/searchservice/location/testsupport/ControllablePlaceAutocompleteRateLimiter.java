package com.roadscanner.searchservice.location.testsupport;

import com.roadscanner.searchservice.location.domain.port.out.PlaceAutocompleteRateLimiter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link PlaceAutocompleteRateLimiter} whose verdict the test decides.
 *
 * <p>Lets application-layer tests assert <em>where</em> the limiter sits in the flow — that a cache
 * hit never consumes a permit, and that a refusal stops the provider call — without depending on
 * the real bucket's timing. The bucket's own arithmetic is covered by
 * {@code TokenBucketPlaceAutocompleteRateLimiterTest}.
 */
public final class ControllablePlaceAutocompleteRateLimiter implements PlaceAutocompleteRateLimiter {

    private final AtomicInteger acquireAttempts = new AtomicInteger();
    private volatile boolean allow = true;

    @Override
    public boolean tryAcquire() {
        acquireAttempts.incrementAndGet();
        return allow;
    }

    public ControllablePlaceAutocompleteRateLimiter exhausted() {
        this.allow = false;
        return this;
    }

    /** How many times a permit was requested — zero proves the limiter was never consulted. */
    public int acquireAttempts() {
        return acquireAttempts.get();
    }
}
