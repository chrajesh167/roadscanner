package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The narrow shape returned in autocomplete results — just enough to render a suggestion row and
 * carry the id forward into the next request.
 *
 * <p>Separate from {@link LocationResponse} on purpose: an autocomplete fires on every keystroke,
 * so its payload should not carry timestamps, coordinates and audit fields no dropdown displays.
 */
@Schema(name = "LocationSummary", description = "Compact location, for autocomplete suggestions")
public record LocationSummary(

        @Schema(description = "Canonical RoadScanner location id")
        String id,

        String displayName,
        String city,
        String state,
        String country
) {

    public static LocationSummary from(Location location) {
        return new LocationSummary(
                location.id().toString(),
                location.displayName(),
                location.address().city(),
                location.address().state(),
                location.address().country());
    }
}
