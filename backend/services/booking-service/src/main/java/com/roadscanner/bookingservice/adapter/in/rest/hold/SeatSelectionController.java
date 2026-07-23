package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.roadscanner.bookingservice.adapter.in.rest.RequesterContextResolver;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.GetSeatSelectionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Composes {@code inventory-service}'s static seat layout with
 * {@code provider-integration-service}'s live per-seat status
 * (docs/services/inventory-service/sequence-diagrams.md flow 4, assigned to this service). Under
 * {@code /api/v1/bookings/...} rather than {@code /api/v1/inventory/...} — deliberately not
 * colliding with {@code inventory-service}'s own, unchanged
 * {@code GET /api/v1/inventory/trips/{tripId}/seat-layout} (docs/services/booking-service/api-summary.md).
 * {@code TRAVELER}-only, matching that document's row exactly — corrected by final implementation
 * audit (previously required authentication only, any role). */
@RestController
@RequestMapping("/api/v1/bookings/trips/{tripId}/seats")
@Tag(name = "Seat Selection", description = "Composed static layout + live status for a trip")
class SeatSelectionController {

    private final GetSeatSelectionView getSeatSelectionView;

    SeatSelectionController(GetSeatSelectionView getSeatSelectionView) {
        this.getSeatSelectionView = getSeatSelectionView;
    }

    @GetMapping
    @Operation(summary = "Get seat selection view", description = "Static seat layout merged with live per-seat status.")
    SeatSelectionViewResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId) {
        requireTraveler(jwt);
        GetSeatSelectionView.Result result = getSeatSelectionView.get(
                new GetSeatSelectionView.Command(new TripId(tripId)));
        return SeatSelectionViewResponse.from(result);
    }

    private void requireTraveler(Jwt jwt) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        if (requester.role() != Role.TRAVELER) {
            throw new AccessDeniedException("Only travelers may view the seat selection view");
        }
    }
}
