package com.roadscanner.searchservice.testsupport.fakes;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.AvailabilityCache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory AvailabilityCache that records what was cached, for assertions. */
public final class InMemoryAvailabilityCache implements AvailabilityCache {

    private final Map<TripId, AvailabilityStatus> cached = new ConcurrentHashMap<>();

    @Override
    public Optional<AvailabilityStatus> get(TripId tripId) {
        return Optional.ofNullable(cached.get(tripId));
    }

    @Override
    public void put(TripId tripId, AvailabilityStatus status) {
        cached.put(tripId, status);
    }

    public boolean wasCached(TripId tripId) {
        return cached.containsKey(tripId);
    }
}
