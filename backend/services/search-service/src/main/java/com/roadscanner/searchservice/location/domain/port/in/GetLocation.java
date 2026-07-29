package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;

import java.util.Objects;

/**
 * Resolves one catalogue entry by its RoadScanner id.
 *
 * <p>Deliberately returns soft-deleted locations too: a historical booking referencing a
 * withdrawn stop must still render. Only the autocomplete path hides inactive entries.
 */
public interface GetLocation {

    GetLocationResult get(GetLocationCommand command);

    record GetLocationCommand(LocationId locationId) {
        public GetLocationCommand {
            Objects.requireNonNull(locationId, "locationId must not be null");
        }
    }

    /**
     * @throws com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException
     *         if no location with this id exists.
     */
    record GetLocationResult(Location location) {
        public GetLocationResult {
            Objects.requireNonNull(location, "location must not be null");
        }
    }
}
