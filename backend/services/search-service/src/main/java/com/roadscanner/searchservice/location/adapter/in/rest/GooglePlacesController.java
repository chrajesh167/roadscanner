package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.port.in.SearchPlaceSuggestions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-side proxy for external place autocomplete.
 *
 * <p>This endpoint exists so the browser never holds the Google API key. The SPA calls
 * RoadScanner; RoadScanner calls Google. A key shipped to the frontend would be readable by
 * anyone with devtools and billable to this project — see {@code GooglePlacesProperties}.
 *
 * <p>Public, matching the rest of the read surface: a traveller typing a destination is not
 * logged in. It is the writes to the catalogue that require {@code ROLE_ADMIN}, and this endpoint
 * writes nothing — it cannot create a location, and it cannot create a provider mapping.
 */
@RestController
@RequestMapping("/api/v1/google/places")
@Validated
@Tag(name = "Place Autocomplete", description = "External place suggestions, enriched with catalogue identity")
class GooglePlacesController {

    private static final int DEFAULT_LIMIT = 5;

    private final SearchPlaceSuggestions searchPlaceSuggestions;

    GooglePlacesController(SearchPlaceSuggestions searchPlaceSuggestions) {
        this.searchPlaceSuggestions = searchPlaceSuggestions;
    }

    @GetMapping
    @Operation(summary = "Autocomplete places",
            description = "Proxies an external place-autocomplete provider and annotates any "
                    + "suggestion that already exists in the RoadScanner catalogue with its "
                    + "canonical location id. Read-only: nothing is added to the catalogue, and no "
                    + "provider mapping is ever created.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suggestions, best match first (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Blank query or out-of-range limit"),
            @ApiResponse(responseCode = "503", description = "The place provider is unavailable or not configured")
    })
    PlaceAutocompleteResponse autocomplete(
            @Parameter(description = "Search fragment", example = "hyd")
            @RequestParam("q") @NotBlank(message = "q must not be blank") String query,

            @Parameter(description = "Maximum suggestions to return (1-10)", example = "5")
            @RequestParam(required = false) @Min(1) @Max(10) Integer limit) {

        int resolvedLimit = limit != null ? limit : DEFAULT_LIMIT;
        SearchPlaceSuggestions.SearchPlaceSuggestionsResult result = searchPlaceSuggestions.search(
                new SearchPlaceSuggestions.SearchPlaceSuggestionsCommand(query, resolvedLimit));

        return PlaceAutocompleteResponse.from(result.suggestions(), result.cached());
    }
}
