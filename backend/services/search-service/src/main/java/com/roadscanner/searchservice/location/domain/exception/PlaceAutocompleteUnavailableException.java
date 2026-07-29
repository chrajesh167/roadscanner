package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;

/**
 * The external place-autocomplete provider could not be reached, timed out, or answered with a
 * failure status.
 *
 * <p>Distinct from "no results" on purpose. Collapsing the two would let an outage look like a
 * legitimately empty dropdown — which is not only misleading to the user, it would also make the
 * outage cacheable. Keeping it an exception is what lets the use case cache successes only.
 */
public final class PlaceAutocompleteUnavailableException extends SearchServiceException {

    public PlaceAutocompleteUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public PlaceAutocompleteUnavailableException(String message) {
        super(message);
    }
}
