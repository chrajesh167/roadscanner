package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.Location;

import java.util.List;
import java.util.Objects;

/**
 * Autocomplete over the catalogue — the read path behind {@code GET /api/v1/locations?q=}.
 *
 * <p>Returns full {@link Location} aggregates; the REST adapter narrows them to a summary shape.
 * Keeping the port in domain terms means a future non-REST caller (a gRPC facade, an internal
 * resolver) gets the same answer without a DTO in the way.
 */
public interface SearchLocations {

    SearchLocationsResult search(SearchLocationsCommand command);

    /**
     * @param query the caller's fragment; blank is rejected here rather than silently returning
     *              the whole catalogue
     * @param limit maximum suggestions to return
     */
    record SearchLocationsCommand(String query, int limit) {
        public SearchLocationsCommand {
            Objects.requireNonNull(query, "query must not be null");
            query = query.trim();
            if (query.isEmpty()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be at least 1");
            }
        }
    }

    record SearchLocationsResult(List<Location> locations) {
        public SearchLocationsResult {
            Objects.requireNonNull(locations, "locations must not be null");
            locations = List.copyOf(locations);
        }
    }
}
