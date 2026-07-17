package com.roadscanner.searchservice.domain.port.in;

import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.TripSearchResult;

import java.util.Objects;

/**
 * The primary client-facing use case — "Search Trips"
 * (docs/services/search-service/use-cases.md). Implementation queries the local index, applies
 * ranking, and overlays live availability per result — see
 * docs/services/search-service/sequence-diagrams.md §4 for the full flow this port's
 * implementation is expected to follow.
 */
public interface SearchTrips {

    SearchTripsResult search(SearchTripsCommand command);

    record SearchTripsCommand(SearchQuery query) {
        public SearchTripsCommand {
            Objects.requireNonNull(query, "query must not be null");
        }
    }

    record SearchTripsResult(ResultPage<TripSearchResult> results) {
        public SearchTripsResult {
            Objects.requireNonNull(results, "results must not be null");
        }
    }
}
