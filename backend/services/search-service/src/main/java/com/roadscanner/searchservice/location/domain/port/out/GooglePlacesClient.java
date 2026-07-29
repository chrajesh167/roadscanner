package com.roadscanner.searchservice.location.domain.port.out;

import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;

import java.util.List;

/**
 * Outbound port for an external place-autocomplete provider. Implemented by
 * {@code location.adapter.out.googleplaces.GooglePlacesAdapter}.
 *
 * <p>Named for Google because Google is what Sprint 2 integrates, but the port itself says
 * nothing Google-specific: it takes a fragment and returns {@link PlaceSuggestion}s. Swapping in
 * Mapbox or HERE later is a new adapter, not a change to the use case.
 *
 * <p><strong>This port never writes anything.</strong> It cannot create a {@code Location} and it
 * cannot create a provider mapping — enrichment suggests, the admin catalogue API decides.
 */
public interface GooglePlacesClient {

    /**
     * Autocomplete against the provider.
     *
     * @param query  the caller's fragment; already validated non-blank upstream
     * @param limit  maximum suggestions wanted; the adapter may return fewer
     * @return suggestions, best match first — empty when the provider has no match
     * @throws com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteUnavailableException
     *         when the provider could not be reached or answered with a failure. Callers must not
     *         treat this as "no results": an outage and a genuine empty result mean different
     *         things, and only one of them is safe to cache.
     */
    List<PlaceSuggestion> autocomplete(String query, int limit);
}
