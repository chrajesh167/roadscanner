package com.roadscanner.searchservice.adapter.in.rest.detail;

import com.roadscanner.searchservice.adapter.in.rest.search.TripResponse;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.GetTripDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Trip-detail lookup — the same trip-plus-availability composition "Search Trips" performs per
 * result row, applied to a single trip (docs/services/search-service/use-cases.md). Deliberately
 * does not return seat-map detail — that's {@code inventory-service} + {@code customer-web}'s
 * surface, direct (docs/services/search-service/boundaries.md).
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Trip Detail", description = "Look up a single indexed trip")
class TripDetailController {

    private final GetTripDetail getTripDetail;

    TripDetailController(GetTripDetail getTripDetail) {
        this.getTripDetail = getTripDetail;
    }

    @GetMapping("/trips/{tripId}")
    @Operation(summary = "Get trip detail", description = "The indexed trip plus its live availability overlay.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trip found"),
            @ApiResponse(responseCode = "404", description = "No trip indexed with this id")
    })
    TripResponse getDetail(@PathVariable UUID tripId) {
        GetTripDetail.GetTripDetailResult result = getTripDetail.getDetail(
                new GetTripDetail.GetTripDetailCommand(new TripId(tripId)));
        return TripResponse.from(result.result());
    }
}
