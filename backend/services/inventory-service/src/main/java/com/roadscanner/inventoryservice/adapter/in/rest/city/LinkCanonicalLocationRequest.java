package com.roadscanner.inventoryservice.adapter.in.rest.city;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The canonical location a catalog city is being declared to be. */
public record LinkCanonicalLocationRequest(
        @Schema(description = "search-service's canonical location id for this city",
                example = "11a059ad-77fc-4b40-b8de-37f20ddb4fec")
        @NotNull UUID canonicalLocationId) {
}
