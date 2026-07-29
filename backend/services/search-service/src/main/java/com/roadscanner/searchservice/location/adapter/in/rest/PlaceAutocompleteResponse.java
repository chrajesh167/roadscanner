package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Envelope for place-autocomplete results, wrapping the list for the same reason
 * {@link AutocompleteResponse} does — room to add fields without breaking every client.
 */
@Schema(name = "PlaceAutocompleteResponse", description = "External place-autocomplete suggestions")
public record PlaceAutocompleteResponse(

        @Schema(description = "Matching places, best match first") List<PlaceSuggestionResponse> suggestions,

        @Schema(description = "True when served from cache rather than a live provider call. "
                + "Exposed so the hit rate on a metered API is observable from the outside.")
        boolean cached) {

    public static PlaceAutocompleteResponse from(List<PlaceSuggestion> suggestions, boolean cached) {
        return new PlaceAutocompleteResponse(suggestions.stream().map(PlaceSuggestionResponse::from).toList(), cached);
    }
}
