package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;

import java.util.List;
import java.util.Objects;

/** Searches the provider identified by an already-open session for trips matching
 * {@link SearchCriteria}. This service's own "Search Trips" — distinct from and upstream of
 * {@code search-service}'s identically-named client-facing use case, which aggregates across
 * providers via this one. */
public interface SearchTrips {

    Result search(Command command);

    record Command(ProviderSessionId sessionId, SearchCriteria criteria) {
        public Command {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(criteria, "criteria must not be null");
        }
    }

    record Result(List<ProviderTrip> trips) {
        public Result {
            Objects.requireNonNull(trips, "trips must not be null");
            trips = List.copyOf(trips);
        }
    }
}
