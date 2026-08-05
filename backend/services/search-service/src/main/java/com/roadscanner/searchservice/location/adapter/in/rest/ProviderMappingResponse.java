package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A mapping on the wire, with the canonical location it translates already resolved.
 *
 * <p>Carrying the location's display name and city rather than only its id is what makes a list of
 * these readable: an operator reconciling "Hyderabad ↔ FlixBus city 3da253ae" needs both halves,
 * and a table of UUID pairs is not something anyone can check by eye.
 *
 * <p><strong>Admin-only.</strong> This is the one response shape in the service that exposes
 * provider identifiers, and it is reachable exclusively under {@code ROLE_ADMIN}. Nothing on the
 * traveller-facing surface returns these values — that containment is the location module's whole
 * reason for existing.
 */
@Schema(name = "ProviderMapping", description = "How one canonical location is expressed in one provider's vocabulary")
public record ProviderMappingResponse(

        String id,
        String provider,

        @Schema(description = "Canonical RoadScanner location id")
        String locationId,
        String locationDisplayName,
        String locationCity,

        String providerCityId,
        String providerStationId,
        String providerStationName,

        @Schema(description = "Opaque provider payload, returned exactly as stored")
        String providerMetadata,

        boolean verified,

        @Schema(description = "When this mapping was last refreshed from the provider; null if never")
        Instant lastSynced,

        Instant createdAt,
        Instant updatedAt
) {

    public static ProviderMappingResponse from(ProviderLocationMapping mapping, Location location) {
        return new ProviderMappingResponse(
                mapping.id().toString(),
                mapping.provider().value(),
                mapping.locationId().toString(),
                location.displayName(),
                location.address().city(),
                mapping.placeRef().cityId(),
                mapping.placeRef().stationId(),
                mapping.placeRef().stationName(),
                mapping.metadataJson().orElse(null),
                mapping.isVerified(),
                mapping.lastSynced().orElse(null),
                mapping.createdAt(),
                mapping.updatedAt());
    }
}
