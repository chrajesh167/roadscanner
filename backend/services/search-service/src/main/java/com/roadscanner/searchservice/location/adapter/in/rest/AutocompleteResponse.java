package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Envelope for autocomplete results.
 *
 * <p>An object rather than a bare array: it leaves room to add paging or a relevance hint later
 * without breaking every client, the same reason {@code SearchSuggestionsResponse} wraps its list.
 */
@Schema(name = "AutocompleteResponse", description = "Location autocomplete suggestions")
public record AutocompleteResponse(
        @Schema(description = "Matching locations, best match first") List<LocationSummary> suggestions) {

    public static AutocompleteResponse from(List<Location> locations) {
        return new AutocompleteResponse(locations.stream().map(LocationSummary::from).toList());
    }
}
