package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;

import java.util.Objects;

/**
 * Edits an existing catalogue entry (admin).
 *
 * <p>Full-snapshot replace, not a patch — an omitted optional field clears it. That matches the
 * PUT verb the REST adapter exposes and mirrors {@code SearchableTrip.applyUpdate}'s
 * replace-not-merge semantics, so "what was sent is what is stored" holds everywhere.
 */
public interface UpdateLocation {

    UpdateLocationResult update(UpdateLocationCommand command);

    record UpdateLocationCommand(LocationId locationId, String displayName, LocationAddress address,
                                 GeoCoordinates coordinates, GooglePlaceId googlePlaceId, String timezone) {
        public UpdateLocationCommand {
            Objects.requireNonNull(locationId, "locationId must not be null");
            Objects.requireNonNull(displayName, "displayName must not be null");
            Objects.requireNonNull(address, "address must not be null");
        }
    }

    /**
     * @throws com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException
     *         if no location with this id exists.
     * @throws com.roadscanner.searchservice.location.domain.exception.DuplicateGooglePlaceIdException
     *         if the supplied Google place id already belongs to a different location.
     */
    record UpdateLocationResult(Location location) {
        public UpdateLocationResult {
            Objects.requireNonNull(location, "location must not be null");
        }
    }
}
