package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;

/**
 * No mapping exists between a location and a given provider.
 *
 * <p>A legitimate, expected state — not every provider serves every place — so callers that can
 * cope with absence should prefer the {@code Optional}-returning repository method. This exists
 * for the callers that genuinely cannot proceed without one.
 */
public final class ProviderMappingNotFoundException extends SearchServiceException {

    private final LocationId locationId;
    private final ProviderCode provider;

    public ProviderMappingNotFoundException(LocationId locationId, ProviderCode provider) {
        super("No " + provider + " mapping for location " + locationId);
        this.locationId = locationId;
        this.provider = provider;
    }

    public LocationId locationId() {
        return locationId;
    }

    public ProviderCode provider() {
        return provider;
    }
}
