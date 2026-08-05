package com.roadscanner.searchservice.location.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Write shape for a provider location mapping.
 *
 * <p>{@code locationId} and {@code provider} are accepted on create and <strong>ignored on
 * update</strong> — together they identify which translation this is, and changing either would
 * silently turn one mapping into a different one while carrying its verified flag and sync history
 * across. Re-pointing a mapping is a delete plus a create. Both columns are additionally
 * {@code updatable = false} on the entity, so the rule holds even if this contract is bypassed.
 *
 * <p>The three provider fields are individually optional because providers model geography
 * inconsistently — some expose only cities, others only stations. The rule that a mapping must
 * carry at least one of the two identifiers lives in {@code ProviderPlaceRef}, where a non-REST
 * caller cannot skip it.
 */
@Schema(name = "ProviderMappingRequest", description = "Create or update a provider location mapping")
public record ProviderMappingRequest(

        @Schema(description = "Canonical RoadScanner location. Required on create, ignored on update.")
        UUID locationId,

        @Schema(description = "Provider code. Required on create, ignored on update.", example = "FLIXBUS")
        @Size(max = 50, message = "provider must be at most 50 characters")
        String provider,

        @Schema(description = "The provider's own id for the city", example = "3da253ae-02ca-430c-87e5-22842065a77d")
        @Size(max = 255, message = "providerCityId must be at most 255 characters")
        String providerCityId,

        @Schema(description = "The provider's own id for the station")
        @Size(max = 255, message = "providerStationId must be at most 255 characters")
        String providerStationId,

        @Schema(description = "The station name the provider prints on a ticket", example = "MGBS")
        @Size(max = 255, message = "providerStationName must be at most 255 characters")
        String providerStationName,

        @Schema(description = "Opaque provider payload, stored as JSON and never parsed")
        String providerMetadata,

        @Schema(description = "Whether a human has confirmed this mapping is correct")
        @NotNull(message = "verified is required")
        Boolean verified
) {
}
