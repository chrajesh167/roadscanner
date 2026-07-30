package com.roadscanner.searchservice.location.adapter.out.ratelimit;

import com.roadscanner.searchservice.config.GooglePlacesProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bucket's behaviour under a controllable clock, so refill can be asserted without sleeping
 * through real minutes.
 */
class TokenBucketPlaceAutocompleteRateLimiterTest {

    private static final Instant START = Instant.parse("2026-07-30T12:00:00Z");

    /** A clock the test advances by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = START;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static GooglePlacesProperties properties(int perMinute, int burst) {
        return new GooglePlacesProperties(true, "test-key", "https://places.test", Duration.ofSeconds(2),
                Duration.ofMinutes(10), 5_000, "en", "IN", perMinute, burst);
    }

    private TokenBucketPlaceAutocompleteRateLimiter limiter(int perMinute, int burst, Clock clock) {
        return new TokenBucketPlaceAutocompleteRateLimiter(properties(perMinute, burst), clock);
    }

    @Test
    void allowsUpToTheBurstImmediately() {
        var limiter = limiter(60, 5, new MutableClock());

        // Starts full: a freshly booted instance has spent no quota, and starting empty would
        // refuse legitimate traffic for a minute after every deploy.
        assertThat(IntStream.range(0, 5).allMatch(i -> limiter.tryAcquire())).isTrue();
    }

    @Test
    void refusesOnceTheBurstIsExhausted() {
        var limiter = limiter(60, 3, new MutableClock());
        IntStream.range(0, 3).forEach(i -> limiter.tryAcquire());

        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.rejectionCount()).isEqualTo(1);
    }

    @Test
    void refillsOverTime() {
        MutableClock clock = new MutableClock();
        var limiter = limiter(60, 3, clock);
        IntStream.range(0, 3).forEach(i -> limiter.tryAcquire());
        assertThat(limiter.tryAcquire()).isFalse();

        // 60/minute is one per second.
        clock.advance(Duration.ofSeconds(2));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void neverAccumulatesBeyondTheBurstSize() {
        MutableClock clock = new MutableClock();
        var limiter = limiter(600, 4, clock);

        // Idle for an hour — a fixed window would now permit an hour's worth at once.
        clock.advance(Duration.ofHours(1));

        assertThat(IntStream.range(0, 4).allMatch(i -> limiter.tryAcquire())).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void sustainsTheConfiguredRateOverTime() {
        MutableClock clock = new MutableClock();
        var limiter = limiter(60, 1, clock);
        int allowed = 0;

        // Ten seconds, sampled every second, at one permit per second.
        for (int second = 0; second < 10; second++) {
            if (limiter.tryAcquire()) {
                allowed++;
            }
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(allowed).isEqualTo(10);
    }

    @Test
    void countsEveryRejection() {
        var limiter = limiter(60, 1, new MutableClock());
        limiter.tryAcquire();

        IntStream.range(0, 5).forEach(i -> limiter.tryAcquire());

        assertThat(limiter.rejectionCount()).isEqualTo(5);
    }

    @Test
    void neverIssuesMorePermitsThanCapacityUnderConcurrency() throws Exception {
        // The CAS loop must not double-spend a permit when threads race — over-issuing is exactly
        // the overspend this class exists to prevent.
        var limiter = limiter(60, 50, new MutableClock());
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    for (int attempt = 0; attempt < 20; attempt++) {
                        if (limiter.tryAcquire()) {
                            granted.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // Clock never advances, so no refill: capacity is the hard ceiling.
        assertThat(granted.get()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidConfiguration() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> properties(0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate-limit-requests-per-minute");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> properties(60, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate-limit-burst-size");
    }
}
