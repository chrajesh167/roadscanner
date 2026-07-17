package com.roadscanner.searchservice.domain.port.out;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;

import java.util.Optional;

/**
 * The short-TTL Redis cache in front of {@link AvailabilityClient}
 * (docs/architecture/high-level-design.md §7, docs/services/search-service/boundaries.md and
 * data-ownership.md's "cache of a cache"). A derived, expendable copy — if this cache is
 * unavailable, the caller falls through to {@link AvailabilityClient} directly; there is no
 * scenario where a cache failure should be treated as "availability unknown" on its own, only
 * as "not cached; fetch live."
 */
public interface AvailabilityCache {

    Optional<AvailabilityStatus> get(TripId tripId);

    void put(TripId tripId, AvailabilityStatus status);
}
