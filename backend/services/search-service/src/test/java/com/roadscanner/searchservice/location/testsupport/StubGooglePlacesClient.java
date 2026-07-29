package com.roadscanner.searchservice.location.testsupport;

import com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteUnavailableException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.out.GooglePlacesClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub {@link GooglePlacesClient} for application-layer tests: answers with canned suggestions,
 * or fails on demand so the "never cache a failure" rule can be asserted directly.
 */
public final class StubGooglePlacesClient implements GooglePlacesClient {

    private List<PlaceSuggestion> response = List.of();
    private boolean failing;
    private final List<String> calls = new ArrayList<>();

    @Override
    public List<PlaceSuggestion> autocomplete(String query, int limit) {
        calls.add(query + "|" + limit);
        if (failing) {
            throw new PlaceAutocompleteUnavailableException("stubbed provider failure");
        }
        return response.size() > limit ? List.copyOf(response.subList(0, limit)) : List.copyOf(response);
    }

    public StubGooglePlacesClient returning(PlaceSuggestion... suggestions) {
        this.response = List.of(suggestions);
        this.failing = false;
        return this;
    }

    public StubGooglePlacesClient failing() {
        this.failing = true;
        return this;
    }

    /** Every call as {@code query|limit} — lets a test prove a cache hit skipped the provider. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    public static PlaceSuggestion suggestion(String placeId, String description) {
        return PlaceSuggestion.uncurated(new GooglePlaceId(placeId), description, description, "Telangana, India");
    }
}
