package com.roadscanner.searchservice.location.domain.exception;

import com.roadscanner.searchservice.domain.exception.SearchServiceException;

/**
 * The place-autocomplete rate limit was exhausted, so the call was refused before reaching the
 * provider.
 *
 * <p>Distinct from {@link PlaceAutocompleteUnavailableException}: this is RoadScanner declining to
 * spend more quota, not the provider failing. They map to different statuses (429 versus 503) and
 * mean different things to a client — one says "slow down", the other says "try again later" — so
 * collapsing them would leave a caller unable to tell whether backing off would help.
 */
public final class PlaceAutocompleteRateLimitedException extends SearchServiceException {

    public PlaceAutocompleteRateLimitedException(String message) {
        super(message);
    }
}
