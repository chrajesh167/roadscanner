package com.roadscanner.providerintegrationservice.adapter.in.rest.search;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchProviderTrips;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The generic provider search surface: one route that works for every provider, present and future.
 *
 * <p>Session-less on purpose. The existing sibling route is scoped to a session because its callers
 * already hold one; this one is not, because requiring a session would force a login on providers
 * whose static partner credential already suffices for search. Each adapter resolves whatever
 * authentication it actually needs — see {@code SearchProviderTrips}.
 *
 * <p>City ids are opaque provider identifiers, resolved by the caller from
 * {@code provider_location_mapping}. This service never translates names to ids, and never learns
 * what a given id means.
 *
 * <p>Additive: nothing about the pre-existing {@code /sessions/{sessionId}/trips} route changed.
 */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/trips")
@Validated
@Tag(name = "Provider Search", description = "Search any provider for trips")
class ProviderTripSearchController {

    private final SearchProviderTrips searchProviderTrips;

    ProviderTripSearchController(SearchProviderTrips searchProviderTrips) {
        this.searchProviderTrips = searchProviderTrips;
    }

    @GetMapping
    @Operation(summary = "Search a provider for trips",
            description = "Searches the given provider using its own city identifiers and returns "
                    + "normalized RoadScanner trips. No authentication session is required by this "
                    + "route; the adapter supplies whatever the provider itself needs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Normalized trips (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed parameters"),
            @ApiResponse(responseCode = "404", description = "Unknown, disabled, or search-incapable provider"),
            @ApiResponse(responseCode = "503", description = "The provider could not be reached")
    })
    SearchTripsResponse search(
            @PathVariable String providerType,

            @Parameter(description = "The provider's own id for the departure city", example = "58291")
            @RequestParam @NotBlank String fromCityId,

            @Parameter(description = "The provider's own id for the arrival city", example = "41100")
            @RequestParam @NotBlank String toCityId,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(example = "2026-08-01") LocalDate departureDate) {

        SearchProviderTrips.Result result = searchProviderTrips.search(new SearchProviderTrips.Command(
                new ProviderType(providerType), new SearchCriteria(fromCityId, toCityId, departureDate)));

        return SearchTripsResponse.fromTrips(result.trips());
    }
}
