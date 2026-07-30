package com.roadscanner.searchservice.location.adapter.out.ratelimit;

import com.roadscanner.searchservice.config.GooglePlacesProperties;
import com.roadscanner.searchservice.location.domain.port.out.PlaceAutocompleteRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket over the place-autocomplete provider: {@code requestsPerMinute} permits refilled
 * continuously, with {@code burstSize} the most that can accumulate.
 *
 * <p>A bucket rather than a fixed window because autocomplete traffic is inherently bursty — a
 * user types six characters in a second and then pauses. A fixed window either rejects that normal
 * burst or, if sized to allow it, permits twice the intended rate across a window boundary. The
 * bucket lets a burst through while holding the sustained rate.
 *
 * <p>Refill is computed from elapsed time on each call rather than by a scheduled task: no timer
 * thread, no drift, and an idle limiter costs nothing.
 *
 * <p><strong>Per instance, not per cluster.</strong> With N replicas the effective ceiling is
 * {@code N × requestsPerMinute}, so the configured value is a per-instance budget and must be set
 * with the replica count in mind. That is a deliberate trade: a Redis round trip on every
 * keystroke to enforce a global limit would add latency to the exact path this exists to protect,
 * and the shared cache already absorbs most repeat traffic. If a hard global cap becomes necessary,
 * a Redis-backed implementation of the port replaces this class without touching the use case.
 */
@Component
public class TokenBucketPlaceAutocompleteRateLimiter implements PlaceAutocompleteRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketPlaceAutocompleteRateLimiter.class);
    private static final long NANOS_PER_MINUTE = 60_000_000_000L;

    private final long capacity;
    private final double tokensPerNano;
    private final Clock clock;

    /** Tokens scaled by {@link #SCALE} so fractional refill survives in a long CAS loop. */
    private final AtomicLong scaledTokens;
    private final AtomicLong lastRefillNanos;
    private final AtomicLong rejections = new AtomicLong();

    private static final long SCALE = 1_000_000L;

    public TokenBucketPlaceAutocompleteRateLimiter(GooglePlacesProperties properties, Clock clock) {
        this.capacity = properties.rateLimitBurstSize();
        this.tokensPerNano = (double) properties.rateLimitRequestsPerMinute() / NANOS_PER_MINUTE;
        this.clock = clock;
        // Starts full: a service that has just booted has consumed no quota, and starting empty
        // would reject legitimate traffic for the first minute after every deploy.
        this.scaledTokens = new AtomicLong(capacity * SCALE);
        this.lastRefillNanos = new AtomicLong(nanoTime());

        log.info("Place autocomplete rate limit active: {} requests/minute, burst {}",
                properties.rateLimitRequestsPerMinute(), properties.rateLimitBurstSize());
    }

    @Override
    public boolean tryAcquire() {
        refill();

        while (true) {
            long current = scaledTokens.get();
            if (current < SCALE) {
                long total = rejections.incrementAndGet();
                // Logged at warn: hitting this means real traffic is being refused, which is
                // either an attack, a client bug, or a limit set too low — all worth seeing.
                log.warn("Place autocomplete rate limit exceeded — request refused (totalRefused={})", total);
                return false;
            }
            if (scaledTokens.compareAndSet(current, current - SCALE)) {
                return true;
            }
            // Lost the race to a concurrent caller; re-read and try again.
        }
    }

    private void refill() {
        long now = nanoTime();
        long last = lastRefillNanos.get();
        long elapsed = now - last;
        if (elapsed <= 0) {
            return;
        }
        if (!lastRefillNanos.compareAndSet(last, now)) {
            // Another thread is refilling for this interval; letting it win avoids double-crediting.
            return;
        }

        long replenished = (long) (elapsed * tokensPerNano * SCALE);
        if (replenished <= 0) {
            return;
        }
        scaledTokens.accumulateAndGet(replenished,
                (current, added) -> Math.min(capacity * SCALE, current + added));
    }

    /** Injected {@link Clock} keeps the limiter testable without sleeping through real minutes. */
    private long nanoTime() {
        return clock.instant().getEpochSecond() * 1_000_000_000L + clock.instant().getNano();
    }

    /** Total refusals since startup — surfaced for tests and for an operator asking "is this us?". */
    public long rejectionCount() {
        return rejections.get();
    }
}
