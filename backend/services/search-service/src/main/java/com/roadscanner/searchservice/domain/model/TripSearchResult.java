package com.roadscanner.searchservice.domain.model;

import java.util.Objects;

/**
 * A single result row: an indexed {@link SearchableTrip} paired with its live availability
 * overlay, fetched fresh per query (docs/services/search-service/boundaries.md) — never stored
 * together, only composed at query time by the application layer. Used identically by both the
 * search-results list and the trip-detail lookup, since neither needs anything the other
 * doesn't (docs/services/search-service/use-cases.md: "Live availability lookup is not modeled
 * as its own use case").
 */
public record TripSearchResult(SearchableTrip trip, AvailabilityStatus availability) {

    public TripSearchResult {
        Objects.requireNonNull(trip, "trip must not be null");
        Objects.requireNonNull(availability, "availability must not be null");
    }
}
