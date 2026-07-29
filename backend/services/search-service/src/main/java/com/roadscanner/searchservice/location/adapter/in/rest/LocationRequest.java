package com.roadscanner.searchservice.location.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Write shape for {@code POST} and {@code PUT}.
 *
 * <p>Jakarta constraints here are the first line only — they give the caller precise per-field
 * errors. The domain value objects re-validate everything independently, so a non-REST caller
 * cannot bypass a rule by skipping this DTO.
 */
@Schema(name = "LocationRequest", description = "Create or replace a location catalogue entry")
public record LocationRequest(

        @Schema(description = "Human-readable name shown to travellers", example = "Hyderabad")
        @NotBlank(message = "displayName is required")
        @Size(max = 255, message = "displayName must be at most 255 characters")
        String displayName,

        @Schema(description = "City this location belongs to", example = "Hyderabad")
        @NotBlank(message = "city is required")
        @Size(max = 120, message = "city must be at most 120 characters")
        String city,

        @Schema(description = "State or province, where applicable", example = "Telangana")
        @Size(max = 120, message = "state must be at most 120 characters")
        String state,

        @Schema(description = "Country", example = "India")
        @NotBlank(message = "country is required")
        @Size(max = 120, message = "country must be at most 120 characters")
        String country,

        @Schema(description = "Latitude; must be supplied together with longitude", example = "17.3850000")
        @DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "latitude must be between -90 and 90")
        BigDecimal latitude,

        @Schema(description = "Longitude; must be supplied together with latitude", example = "78.4867000")
        @DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "longitude must be between -180 and 180")
        BigDecimal longitude,

        @Schema(description = "Google Place ID, if known. Optional, but unique across the catalogue.",
                example = "ChIJx9Lr6tqZyzsRwvu6koO3k64")
        @Size(max = 255, message = "googlePlaceId must be at most 255 characters")
        String googlePlaceId,

        @Schema(description = "IANA timezone identifier", example = "Asia/Kolkata")
        @Size(max = 80, message = "timezone must be at most 80 characters")
        String timezone
) {
}
