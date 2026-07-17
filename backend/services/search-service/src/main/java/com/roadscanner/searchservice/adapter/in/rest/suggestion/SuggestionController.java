package com.roadscanner.searchservice.adapter.in.rest.suggestion;

import com.roadscanner.searchservice.config.SearchProperties;
import com.roadscanner.searchservice.domain.port.in.GetSearchSuggestions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * Autocomplete over place names already present in the index
 * (docs/services/search-service/use-cases.md) — a lightweight aid to composing a search query,
 * not a separate data source.
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@Tag(name = "Suggestions", description = "Autocomplete for origin/destination place names")
class SuggestionController {

    private static final int ABSOLUTE_MAX_RESULTS = 25;

    private final GetSearchSuggestions getSearchSuggestions;
    private final SearchProperties searchProperties;

    SuggestionController(GetSearchSuggestions getSearchSuggestions, SearchProperties searchProperties) {
        this.getSearchSuggestions = getSearchSuggestions;
        this.searchProperties = searchProperties;
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Suggest place names", description = "Distinct origins/destinations of bookable, "
            + "indexed trips starting with the given prefix.")
    @ApiResponse(responseCode = "200", description = "Suggestions (possibly empty)")
    SearchSuggestionsResponse suggest(
            @RequestParam @NotBlank String query,
            @RequestParam(required = false) @Min(1) @Max(ABSOLUTE_MAX_RESULTS) Integer maxResults
    ) {
        int resolvedMax = maxResults != null ? maxResults : searchProperties.suggestions().maxResults();
        GetSearchSuggestions.SearchSuggestionsResult result = getSearchSuggestions.suggest(
                new GetSearchSuggestions.SearchSuggestionsCommand(query, resolvedMax));
        return new SearchSuggestionsResponse(result.suggestions());
    }
}
