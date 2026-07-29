package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;
import com.roadscanner.searchservice.location.domain.model.LocationId;

/**
 * A lookup referenced a {@link LocationId} that is not in the catalogue.
 *
 * <p>Extends the existing {@code SearchServiceException} root rather than introducing a parallel
 * hierarchy — the location module is a module inside search-service, not a service of its own, so
 * it shares one exception root and one {@code GlobalExceptionHandler}.
 */
public final class LocationNotFoundException extends SearchServiceException {

    private final LocationId locationId;

    public LocationNotFoundException(LocationId locationId) {
        super("Location not found: " + locationId);
        this.locationId = locationId;
    }

    public LocationId locationId() {
        return locationId;
    }
}
