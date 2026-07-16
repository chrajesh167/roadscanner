package com.roadscanner.authservice.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A test clock that can be advanced explicitly — for exercising time-dependent rules (lockout
 * windows, token expiry) without sleeping or mocking.
 */
public final class MutableClock extends Clock {

    private Instant instant;

    public MutableClock(Instant start) {
        this.instant = start;
    }

    public void advanceBy(Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("Fixed-zone test clock");
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
