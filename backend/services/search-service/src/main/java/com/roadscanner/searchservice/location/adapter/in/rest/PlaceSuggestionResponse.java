package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One place-autocomplete candidate.
 *
 * <p>{@code locationId} is the field that makes this useful: when it is present, the place is
 * already in the RoadScanner catalogue and the client should carry that id forward — a
 * {@code googlePlaceId} is never an acceptable identifier anywhere else in the platform. When it
 * is null the place is a candidate only, and promoting it is an admin action through
 * {@code POST /api/v1/locations}.
 */
@Schema(name = "PlaceSuggestion", description = "An external place-autocomplete candidate")
public record PlaceSuggestionResponse(

        @Schema(description = "Google's opaque place identifier. Useful only for promoting this "
                + "candidate into the catalogue — never as a platform-wide location identifier.")
        String googlePlaceId,

        @Schema(description = "Full human-readable description", example = "Hyderabad, Telangana, India")
        String description,

        @Schema(description = "Main line for a suggestion row", example = "Hyderabad")
        String primaryText,

        @Schema(description = "Supporting line for a suggestion row", example = "Telangana, India")
        String secondaryText,

        @Schema(description = "The canonical RoadScanner location id, when this place is already "
                + "curated. Null means the place is not in the catalogue yet.")
        String locationId,

        @Schema(description = "True when this place already exists in the RoadScanner catalogue")
        boolean curated
) {

    public static PlaceSuggestionResponse from(PlaceSuggestion suggestion) {
        return new PlaceSuggestionResponse(
                suggestion.googlePlaceId().value(),
                suggestion.description(),
                suggestion.primaryText(),
                suggestion.secondaryText(),
                suggestion.locationIdIfPresent().map(LocationId::toString).orElse(null),
                suggestion.isCurated());
    }
}
