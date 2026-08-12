package com.roadscanner.inventoryservice.adapter.in.rest.providermapping;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.in.ResolveProviderTrip;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolves a provider's own trip id to the catalog trip that represents it — the reverse of
 * {@link ProviderMappingController}.
 *
 * <p>Exists because search returns live provider trips identified only by {@code (providerCode,
 * providerTripId)}, while seat selection and every booking step downstream are keyed by a catalog
 * trip UUID. Without this, a traveller could see a provider trip and have no way to book it; with
 * it, they enter exactly the same booking flow as any catalog trip, which is the point — there is
 * one booking path, not one per origin of the trip.
 *
 * <p>Returns the mapping, not the trip: the caller wants an identity to continue with, and the
 * trip's own detail is already served by the routes that own it. Nothing about the catalog's
 * internals is exposed beyond the id the caller must have to proceed.
 *
 * <p>Under {@code /internal/} for the same reason as every other internal surface here — it is
 * service-to-service, expects {@code api-gateway} never to route it publicly, and carries the same
 * disclosed authentication gap recorded in this platform's other READMEs.
 */
@RestController
@RequestMapping("/internal/api/v1/inventory/provider-trips/{providerType}/{providerTripId}")
@Tag(name = "Provider Mapping", description = "Resolve a catalog trip's provider mapping")
class ProviderTripResolutionController {

    private final ResolveProviderTrip resolveProviderTrip;

    ProviderTripResolutionController(ResolveProviderTrip resolveProviderTrip) {
        this.resolveProviderTrip = resolveProviderTrip;
    }

    @GetMapping
    @Operation(summary = "Resolve a provider trip to a catalog trip",
            description = "Given a provider's own trip id, returns the catalog trip that represents it. "
                    + "404 when catalog sync has not reconciled that provider trip — it is real but not "
                    + "yet bookable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The catalog trip backing this provider trip"),
            @ApiResponse(responseCode = "404", description = "No catalog trip mapped to that provider trip")
    })
    ProviderMappingResponse resolve(
            @Parameter(description = "Provider code, e.g. MOCK", example = "MOCK")
            @PathVariable String providerType,
            @Parameter(description = "The provider's own trip id")
            @PathVariable String providerTripId) {

        ResolveProviderTrip.Result result = resolveProviderTrip.resolve(
                new ResolveProviderTrip.Command(new ProviderType(providerType), providerTripId));
        return ProviderMappingResponse.from(result.mapping());
    }
}
