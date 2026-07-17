package com.roadscanner.searchservice.testsupport.fakes;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.AvailabilityClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Configurable AvailabilityClient double — returns a preset status per trip, defaulting to
 * unknown, and counts how many times it was actually called (to verify caching behavior). */
public final class StubAvailabilityClient implements AvailabilityClient {

    private final Map<TripId, AvailabilityStatus> responses = new ConcurrentHashMap<>();
    private final AtomicInteger callCount = new AtomicInteger();

    public void willReturn(TripId tripId, AvailabilityStatus status) {
        responses.put(tripId, status);
    }

    @Override
    public AvailabilityStatus fetchAvailability(TripId tripId) {
        callCount.incrementAndGet();
        return responses.getOrDefault(tripId, AvailabilityStatus.unknown());
    }

    public int callCount() {
        return callCount.get();
    }
}
