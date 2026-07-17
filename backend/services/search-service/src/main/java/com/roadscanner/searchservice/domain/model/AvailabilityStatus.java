package com.roadscanner.searchservice.domain.model;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * The live-availability overlay for one trip in a search result
 * (docs/services/search-service/boundaries.md) — deliberately not stored on
 * {@link SearchableTrip} itself, since it is fetched fresh per query from
 * {@code inventory-service} (via a cached call), never held in the index. {@link #UNKNOWN}
 * exists specifically for the "degrade, not fail" rule: if {@code inventory-service} or its
 * cache is unreachable, a result is still returned with availability marked unknown rather than
 * failing the whole search (docs/services/search-service/boundaries.md, "Relationship to
 * inventory-service").
 */
public record AvailabilityStatus(OptionalInt seatsAvailable) {

    private static final AvailabilityStatus UNKNOWN = new AvailabilityStatus(OptionalInt.empty());

    public AvailabilityStatus {
        Objects.requireNonNull(seatsAvailable, "seatsAvailable must not be null");
        seatsAvailable.ifPresent(count -> {
            if (count < 0) {
                throw new IllegalArgumentException("seatsAvailable must not be negative");
            }
        });
    }

    public static AvailabilityStatus of(int seatsAvailable) {
        return new AvailabilityStatus(OptionalInt.of(seatsAvailable));
    }

    public static AvailabilityStatus unknown() {
        return UNKNOWN;
    }

    public boolean isKnown() {
        return seatsAvailable.isPresent();
    }
}
