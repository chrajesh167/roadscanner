package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.port.in.ResolveProviderLocations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service translation of canonical locations into one provider's city ids.
 *
 * <p>Under {@code /internal} rather than {@code /api/v1} because it has no human caller: the
 * administrative surface over the same table is unchanged and stays on
 * {@code /api/v1/provider-mappings} behind {@code ROLE_ADMIN}. This route exists so
 * {@code inventory-service}'s catalog sync can ask "what does FlixBus call these places" without a
 * second copy of the mapping table — the defect it fixes is sync sending city <em>names</em> where
 * providers require the ids they issued.
 *
 * <p>Read-only, and narrow on purpose. It exposes no mapping metadata, no verification state and no
 * way to author anything; a caller gets city ids and a list of what could not be translated.
 */
@RestController
@RequestMapping("/internal/api/v1/provider-locations/{provider}")
@Validated
@Tag(name = "Provider Location Resolution",
        description = "Internal: canonical location ids to one provider's city ids")
class ProviderLocationResolutionController {

    private final ResolveProviderLocations resolveProviderLocations;

    ProviderLocationResolutionController(ResolveProviderLocations resolveProviderLocations) {
        this.resolveProviderLocations = resolveProviderLocations;
    }

    @GetMapping("/resolve")
    @Operation(summary = "Resolve canonical locations to provider city ids",
            description = "Returns a city id per location this provider has a verified city mapping "
                    + "for. Locations without one are reported as unresolved rather than guessed at — "
                    + "a mapping that is merely plausible sends a traveller to the wrong city.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resolved ids plus anything unresolvable"),
            @ApiResponse(responseCode = "400", description = "No location ids supplied")
    })
    ProviderLocationResolutionResponse resolve(
            @Parameter(description = "Provider code", example = "FLIXBUS") @PathVariable String provider,
            @Parameter(description = "Canonical RoadScanner location ids")
            @RequestParam @NotEmpty List<UUID> locationIds) {

        ResolveProviderLocations.Result result = resolveProviderLocations.resolve(
                new ResolveProviderLocations.Query(new ProviderCode(provider),
                        locationIds.stream().map(LocationId::new).toList()));

        return ProviderLocationResolutionResponse.from(provider, result);
    }
}
