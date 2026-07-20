package com.roadscanner.providerintegrationservice.adapter.in.rest.seatmap;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.port.in.GetSeatMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal-only — retrieves the seat layout for one provider trip. */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/sessions/{sessionId}/trips/{providerTripId}/seat-map")
@Tag(name = "Provider Seat Map", description = "Retrieve a trip's seat layout")
class ProviderSeatMapController {

    private final GetSeatMap getSeatMap;

    ProviderSeatMapController(GetSeatMap getSeatMap) {
        this.getSeatMap = getSeatMap;
    }

    @GetMapping
    @Operation(summary = "Get seat map", description = "Served from a short-TTL cache when available; falls through to a live provider call on miss.")
    SeatMapResponse getSeatMap(@PathVariable String providerType, @PathVariable UUID sessionId,
                                @PathVariable String providerTripId) {
        GetSeatMap.Result result = getSeatMap.getSeatMap(
                new GetSeatMap.Command(new ProviderSessionId(sessionId), providerTripId));
        return SeatMapResponse.from(result.seatMap());
    }
}
