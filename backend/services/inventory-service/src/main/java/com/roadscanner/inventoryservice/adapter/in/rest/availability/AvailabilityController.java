package com.roadscanner.inventoryservice.adapter.in.rest.availability;

import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.GetTripAvailability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * <strong>The one frozen contract in this service.</strong> {@code search-service}'s
 * {@code AvailabilityClient} already calls exactly this path expecting exactly this response
 * shape (docs/services/inventory-service/api-summary.md) — this method's path, verb, and
 * success-body shape must never change. Internally this is a live pass-through to
 * {@code provider-integration-service} via the trip's {@code ProviderMapping}
 * (docs/services/inventory-service/boundaries.md), never an owned answer.
 *
 * An unknown result degrades to a {@code 503}, not a {@code 200} with a sentinel value — this is
 * deliberate: {@code search-service}'s already-shipped {@code AvailabilityClient} catches any
 * {@code RestClientException} (any non-2xx response) and degrades to "unknown" on its own side,
 * so returning an error status here requires no change to that already-running code.
 */
@RestController
@RequestMapping("/api/v1/inventory/trips/{tripId}/availability")
@Tag(name = "Availability", description = "Live seat-count facade — the contract search-service depends on")
class AvailabilityController {

    private final GetTripAvailability getTripAvailability;

    AvailabilityController(GetTripAvailability getTripAvailability) {
        this.getTripAvailability = getTripAvailability;
    }

    @GetMapping
    @Operation(summary = "Get live trip availability", description = "Proxied live from provider-integration-service via this trip's ProviderMapping; never cached here.")
    ResponseEntity<?> getAvailability(@PathVariable UUID tripId) {
        GetTripAvailability.Result result = getTripAvailability.get(new GetTripAvailability.Command(new TripId(tripId)));
        OptionalInt availableSeats = result.availableSeats();
        if (availableSeats.isPresent()) {
            return ResponseEntity.ok(new AvailabilityResponse(availableSeats.getAsInt()));
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Live availability is currently unknown for this trip");
        problem.setType(URI.create("https://roadscanner.example/problems/availability-unknown"));
        problem.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
