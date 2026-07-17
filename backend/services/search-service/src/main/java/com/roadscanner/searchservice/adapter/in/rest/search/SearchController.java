package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.config.SearchProperties;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.SortOption;
import com.roadscanner.searchservice.domain.port.in.SearchTrips;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The primary client-facing endpoint — "Search Trips" (docs/services/search-service/api-summary.md).
 * Filter and sort parameters are query parameters of this one endpoint, not separate endpoints —
 * matching that document's "presented here as one operation with parameters" note.
 *
 * Structural validation only (presence, range shape) — the semantic invariants (origin ≠
 * destination, minFare ≤ maxFare, etc.) are enforced a second time, unconditionally, by
 * {@link SearchQuery}'s own constructor regardless of what this controller already checked,
 * the same defense-in-depth discipline {@code auth-service}'s value objects apply.
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@Tag(name = "Search", description = "Trip search, filtering, and sorting")
class SearchController {

    private final SearchTrips searchTrips;
    private final SearchProperties searchProperties;

    SearchController(SearchTrips searchTrips, SearchProperties searchProperties) {
        this.searchTrips = searchTrips;
        this.searchProperties = searchProperties;
    }

    @GetMapping("/trips")
    @Operation(summary = "Search trips", description = "FR-2.1–FR-2.3: search by origin/destination/date, "
            + "with optional filtering and sorting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranked, paged results (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Malformed request")
    })
    SearchResultResponse search(
            @RequestParam @NotBlank String origin,
            @RequestParam @NotBlank String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(example = "2026-08-01") LocalDate date,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "minFare must not be negative") BigDecimal minFare,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "maxFare must not be negative") BigDecimal maxFare,
            @RequestParam(required = false) Instant departureAfter,
            @RequestParam(required = false) Instant departureBefore,
            @RequestParam(required = false) String busType,
            @RequestParam(required = false) @Min(0) @Max(5) Double minRating,
            @RequestParam(required = false) SortOption sort,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) Integer size
    ) {
        int resolvedPage = page != null ? page : 0;
        int resolvedSize = resolveSize(size);

        SearchQuery query = new SearchQuery(new Route(origin, destination), date, minFare, maxFare,
                departureAfter, departureBefore, busType, minRating, sort, resolvedPage, resolvedSize);
        SearchTrips.SearchTripsResult result = searchTrips.search(new SearchTrips.SearchTripsCommand(query));
        return SearchResultResponse.from(result.results());
    }

    private int resolveSize(Integer requestedSize) {
        int maxPageSize = searchProperties.pagination().maxPageSize();
        if (requestedSize == null) {
            return searchProperties.pagination().defaultPageSize();
        }
        return Math.min(requestedSize, maxPageSize);
    }
}
