package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;

/**
 * A create or update would have given two catalogue entries the same {@link GooglePlaceId}.
 *
 * <p>Checked in the application layer before writing, so the caller gets a precise 409 rather
 * than a raw constraint-violation surfacing as a 500. The unique index in V2 remains the real
 * guarantee — the check is for a good error message, not for correctness under concurrency.
 */
public final class DuplicateGooglePlaceIdException extends SearchServiceException {

    private final GooglePlaceId googlePlaceId;

    public DuplicateGooglePlaceIdException(GooglePlaceId googlePlaceId) {
        super("Google place id already mapped to another location: " + googlePlaceId);
        this.googlePlaceId = googlePlaceId;
    }

    public GooglePlaceId googlePlaceId() {
        return googlePlaceId;
    }
}
