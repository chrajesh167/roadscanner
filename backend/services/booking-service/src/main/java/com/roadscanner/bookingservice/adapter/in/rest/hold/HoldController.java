package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.roadscanner.bookingservice.adapter.in.rest.RequesterContextResolver;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HoldSeats;
import com.roadscanner.bookingservice.domain.port.in.ReleaseHold;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** {@code Hold Seats} / {@code Release Hold} — {@code TRAVELER}-only, per
 * docs/services/booking-service/api-summary.md. */
@RestController
@RequestMapping("/api/v1/bookings/holds")
@Tag(name = "Seat Holds", description = "Place and release temporary seat holds")
class HoldController {

    private final HoldSeats holdSeats;
    private final ReleaseHold releaseHold;

    HoldController(HoldSeats holdSeats, ReleaseHold releaseHold) {
        this.holdSeats = holdSeats;
        this.releaseHold = releaseHold;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Hold seats", description = "Places a temporary hold on selected seat(s) for a trip.")
    HoldSeatsResponse hold(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HoldSeatsRequest request) {
        RequesterContext requester = requireTraveler(jwt);
        HoldSeats.Result result = holdSeats.hold(new HoldSeats.Command(requester.requesterId(),
                new TripId(request.tripId()), request.seatNumbers()));
        return HoldSeatsResponse.from(result);
    }

    @DeleteMapping("/{seatHoldId}")
    @Operation(summary = "Release a hold", description = "Voluntarily abandon a hold before booking (idempotent).")
    ReleaseHoldResponse release(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID seatHoldId) {
        RequesterContext requester = requireTraveler(jwt);
        ReleaseHold.Result result = releaseHold.release(
                new ReleaseHold.Command(requester.requesterId(), new SeatHoldId(seatHoldId)));
        return new ReleaseHoldResponse(result.released());
    }

    private RequesterContext requireTraveler(Jwt jwt) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        if (requester.role() != Role.TRAVELER) {
            throw new AccessDeniedException("Only travelers may hold or release seats");
        }
        return requester;
    }
}
