package com.roadscanner.inventoryservice.adapter.in.rest.trip;

import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.GetSeatLayout;
import com.roadscanner.inventoryservice.domain.port.in.GetTripMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Catalog trip metadata and static seat layout — never live seat status
 * (docs/services/inventory-service/api-summary.md). */
@RestController
@RequestMapping("/api/v1/inventory/trips/{tripId}")
@Tag(name = "Trips", description = "Catalog trip metadata and static seat layout")
class TripController {

    private final GetTripMetadata getTripMetadata;
    private final GetSeatLayout getSeatLayout;

    TripController(GetTripMetadata getTripMetadata, GetSeatLayout getSeatLayout) {
        this.getTripMetadata = getTripMetadata;
        this.getSeatLayout = getSeatLayout;
    }

    @GetMapping
    @Operation(summary = "Get trip metadata", description = "Catalog shape only — route, schedule, operator, fare, bookable flag.")
    TripResponse getMetadata(@PathVariable UUID tripId) {
        GetTripMetadata.Result result = getTripMetadata.get(new GetTripMetadata.Command(new TripId(tripId)));
        return TripResponse.from(result.trip());
    }

    @GetMapping("/seat-layout")
    @Operation(summary = "Get static seat layout", description = "Seat numbering, deck, type — shape only, never live status.")
    SeatLayoutResponse getSeatLayout(@PathVariable UUID tripId) {
        GetSeatLayout.Result result = getSeatLayout.get(new GetSeatLayout.Command(new TripId(tripId)));
        return SeatLayoutResponse.from(result.seatLayout());
    }
}
