package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;

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
    private final ProviderLocationMappingId mappingId;

    public ProviderMappingNotFoundException(LocationId locationId, ProviderCode provider) {
        super("No " + provider + " mapping for location " + locationId);
        this.locationId = locationId;
        this.provider = provider;
        this.mappingId = null;
    }

    /**
     * Addressed by the mapping's own id — the administrative lookup path.
     *
     * <p>Unlike the (location, provider) form above, this one is unambiguously an error rather
     * than an expected absence: the caller is holding an id it was given, so a miss means the
     * mapping was deleted underneath it, not that this provider happens not to serve the place.
     */
    public ProviderMappingNotFoundException(ProviderLocationMappingId mappingId) {
        super("No provider mapping with id " + mappingId);
        this.locationId = null;
        this.provider = null;
        this.mappingId = mappingId;
    }

    /** Null when the mapping was addressed by its own id. */
    public LocationId locationId() {
        return locationId;
    }

    /** Null when the mapping was addressed by its own id. */
    public ProviderCode provider() {
        return provider;
    }

    /** Null when the mapping was addressed by (location, provider). */
    public ProviderLocationMappingId mappingId() {
        return mappingId;
    }
}
