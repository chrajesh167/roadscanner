package com.roadscanner.searchservice.domain.port.in;

import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.model.TripSearchResult;

import java.util.Objects;

/**
 * Looks up a single indexed trip by id, overlaid with live availability — the same composition
 * "Search Trips" performs per row, applied to one specific trip
 * (docs/services/search-service/use-cases.md). Deliberately does not return seat-map detail —
 * that's {@code inventory-service} + {@code customer-web}'s surface, direct
 * (docs/services/search-service/boundaries.md).
 */
public interface GetTripDetail {

    GetTripDetailResult getDetail(GetTripDetailCommand command);

    record GetTripDetailCommand(TripId tripId) {
        public GetTripDetailCommand {
            Objects.requireNonNull(tripId, "tripId must not be null");
        }
    }

    /**
     * @throws com.roadscanner.searchservice.domain.exception.TripNotFoundException if no trip
     *         with this id is indexed.
     */
    record GetTripDetailResult(TripSearchResult result) {
        public GetTripDetailResult {
            Objects.requireNonNull(result, "result must not be null");
        }
    }
}
