package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Full read shape for a single location.
 *
 * <p>Carries no provider identifier of any kind, deliberately — that is the contract this module
 * exists to enforce. A client that needs to reach a provider goes through a service that resolves
 * the mapping internally; it never receives provider ids itself.
 *
 * <p>{@code googlePlaceId} is exposed because it is a stable public identifier callers may already
 * hold, and Sprint 2 populates it without changing this shape.
 */
@Schema(name = "LocationResponse", description = "A RoadScanner location catalogue entry")
public record LocationResponse(

        @Schema(description = "Canonical RoadScanner location id — the only location identifier "
                + "any other service should use")
        String id,

        String displayName,
        String city,
        String state,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String googlePlaceId,
        String timezone,

        @Schema(description = "False when soft-deleted; such a location is still resolvable by id "
                + "but no longer offered in autocomplete")
        boolean active,

        Instant createdAt,
        Instant updatedAt
) {

    public static LocationResponse from(Location location) {
        GeoCoordinates coordinates = location.coordinates().orElse(null);
        return new LocationResponse(
                location.id().toString(),
                location.displayName(),
                location.address().city(),
                location.address().state(),
                location.address().country(),
                coordinates == null ? null : coordinates.latitude(),
                coordinates == null ? null : coordinates.longitude(),
                location.googlePlaceId().map(GooglePlaceId::value).orElse(null),
                location.timezone().orElse(null),
                location.isActive(),
                location.createdAt(),
                location.updatedAt());
    }
}
