package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;

import java.util.List;
import java.util.Objects;

/**
 * The administrative listing: filtered, paged mappings, each already resolved against the
 * canonical location it translates.
 *
 * <p>Returning the {@link Location} alongside each mapping is the point. A mapping on its own
 * carries a bare {@code LocationId}, and a list of UUIDs beside provider ids is unreadable — the
 * operator is reconciling "Hyderabad ↔ FlixBus city 3da253ae", and both halves have to be
 * present for that to mean anything. Resolving them here, in one batched read, keeps the REST
 * layer from issuing a lookup per row.
 */
public interface SearchProviderMappings {

    Result search(Query query);

    /**
     * Canonical locations with no mapping for this provider — the onboarding worklist.
     *
     * <p>Belongs on this port rather than a separate one because it answers the same operator
     * question from the other side: "what still needs mapping for this provider?" is the
     * complement of "what is mapped?", and splitting them across two ports would put one
     * conversation in two places.
     */
    UnmappedResult findUnmappedLocations(UnmappedQuery query);

    record Query(ProviderCode provider, Boolean verified, String searchTerm, int page, int size) {
        public Query {
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
            if (size < 1) {
                throw new IllegalArgumentException("size must be at least 1");
            }
        }
    }

    /** One row: the translation, plus the place it translates. */
    record MappedLocation(ProviderLocationMapping mapping, Location location) {
        public MappedLocation {
            Objects.requireNonNull(mapping, "mapping must not be null");
            Objects.requireNonNull(location, "location must not be null");
        }
    }

    record Result(List<MappedLocation> rows, long totalElements, int page, int size, int totalPages) {
        public Result {
            rows = List.copyOf(rows);
        }
    }

    record UnmappedQuery(ProviderCode provider, String searchTerm, int limit) {
        public UnmappedQuery {
            Objects.requireNonNull(provider, "provider must not be null");
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be at least 1");
            }
        }
    }

    record UnmappedResult(List<Location> locations) {
        public UnmappedResult {
            locations = List.copyOf(locations);
        }
    }
}
