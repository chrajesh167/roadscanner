package com.roadscanner.searchservice.location.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * One candidate place returned by an external place-autocomplete provider, before anyone has
 * decided it belongs in the catalogue.
 *
 * <p>Deliberately <em>not</em> a {@link Location}. A suggestion is an external system's opinion;
 * a Location is RoadScanner's own master record. Keeping them distinct is what stops an
 * autocomplete response from being mistaken for catalogue truth, and is why this type carries a
 * {@link GooglePlaceId} rather than a {@link LocationId} — the platform mints its own identity
 * only when an admin promotes a suggestion through {@code POST /api/v1/locations}.
 *
 * <p>{@code locationId} is present when this suggestion has already been curated: the resolver
 * matched its Google place id to an existing catalogue entry. That is the whole enrichment
 * story — it tells a caller "you already have this one, here is its RoadScanner id" without
 * Google ever writing to the catalogue.
 */
public record PlaceSuggestion(GooglePlaceId googlePlaceId, String description, String primaryText,
                              String secondaryText, LocationId locationId) {

    public PlaceSuggestion {
        Objects.requireNonNull(googlePlaceId, "googlePlaceId must not be null");
        description = requireNonBlank(description, "description");
        primaryText = requireNonBlank(primaryText, "primaryText");
        // Secondary text ("Telangana, India") is genuinely absent for some place types.
        secondaryText = (secondaryText == null || secondaryText.isBlank()) ? null : secondaryText.trim();
    }

    /** A suggestion as it comes back from the provider — not yet matched against the catalogue. */
    public static PlaceSuggestion uncurated(GooglePlaceId googlePlaceId, String description,
                                            String primaryText, String secondaryText) {
        return new PlaceSuggestion(googlePlaceId, description, primaryText, secondaryText, null);
    }

    /** The same suggestion, once the catalogue turned out to already contain it. */
    public PlaceSuggestion curatedAs(LocationId locationId) {
        Objects.requireNonNull(locationId, "locationId must not be null");
        return new PlaceSuggestion(googlePlaceId, description, primaryText, secondaryText, locationId);
    }

    /** True when this place already exists in the RoadScanner catalogue. */
    public boolean isCurated() {
        return locationId != null;
    }

    public Optional<LocationId> locationIdIfPresent() {
        return Optional.ofNullable(locationId);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
