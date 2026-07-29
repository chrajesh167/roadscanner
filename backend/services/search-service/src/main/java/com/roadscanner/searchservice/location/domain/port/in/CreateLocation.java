package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;

import java.util.Objects;

/**
 * Adds a new entry to the catalogue (admin).
 *
 * <p>The command speaks in domain value objects, not raw strings, so validation happens once — in
 * the value objects themselves — and cannot be bypassed by a caller that isn't the REST adapter.
 */
public interface CreateLocation {

    CreateLocationResult create(CreateLocationCommand command);

    record CreateLocationCommand(String displayName, LocationAddress address, GeoCoordinates coordinates,
                                 GooglePlaceId googlePlaceId, String timezone) {
        public CreateLocationCommand {
            Objects.requireNonNull(displayName, "displayName must not be null");
            Objects.requireNonNull(address, "address must not be null");
            // coordinates, googlePlaceId and timezone are genuinely optional — a curated city
            // entry can exist before anyone has pinned its point or matched it to Google.
        }
    }

    /**
     * @throws com.roadscanner.searchservice.location.domain.exception.DuplicateGooglePlaceIdException
     *         if the supplied Google place id already belongs to another location.
     */
    record CreateLocationResult(Location location) {
        public CreateLocationResult {
            Objects.requireNonNull(location, "location must not be null");
        }
    }
}
