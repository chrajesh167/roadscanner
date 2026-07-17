package com.roadscanner.searchservice.application.usecase.availability;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.model.TripSearchResult;
import com.roadscanner.searchservice.domain.port.out.AvailabilityCache;
import com.roadscanner.searchservice.domain.port.out.AvailabilityClient;

/**
 * The cache-then-live-call composition behind every availability overlay
 * (docs/services/search-service/boundaries.md, docs/services/search-service/sequence-diagrams.md
 * §4) — shared by {@code SearchTripsService} (once per result row) and
 * {@code GetTripDetailService} (once), so this composition exists exactly once rather than
 * duplicated across both use cases. Not itself a use-case-port implementation; a plain
 * collaborator wired directly by {@code config.UseCaseConfig}.
 *
 * A freshly fetched {@link AvailabilityStatus#unknown()} result is deliberately never cached —
 * caching a failure would prolong it for the cache's full TTL even after
 * {@code inventory-service} recovers, which defeats the point of a *short*-TTL cache being able
 * to self-correct quickly.
 */
public class AvailabilityOverlay {

    private final AvailabilityCache cache;
    private final AvailabilityClient client;

    public AvailabilityOverlay(AvailabilityCache cache, AvailabilityClient client) {
        this.cache = cache;
        this.client = client;
    }

    public TripSearchResult overlay(SearchableTrip trip) {
        return new TripSearchResult(trip, resolveAvailability(trip.tripId()));
    }

    private AvailabilityStatus resolveAvailability(TripId tripId) {
        return cache.get(tripId).orElseGet(() -> fetchAndMaybeCache(tripId));
    }

    private AvailabilityStatus fetchAndMaybeCache(TripId tripId) {
        AvailabilityStatus fetched = client.fetchAvailability(tripId);
        if (fetched.isKnown()) {
            cache.put(tripId, fetched);
        }
        return fetched;
    }
}
