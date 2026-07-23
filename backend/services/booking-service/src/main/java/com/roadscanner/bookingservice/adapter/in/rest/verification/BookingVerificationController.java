package com.roadscanner.bookingservice.adapter.in.rest.verification;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.VerifyBooking;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal, service-to-service only — the one inbound call {@code review-service} (not yet
 * built) makes against this service, backing FR-7.2
 * (docs/services/booking-service/boundaries.md's "Relationship to `review-service`"). No
 * authentication on this endpoint in Phase 1 — matches {@code inventory-service}'s and
 * {@code provider-integration-service}'s identical, disclosed {@code /internal/**} gap. */
@RestController
@RequestMapping("/internal/api/v1/bookings/verify")
@Tag(name = "Booking Verification", description = "Service-to-service: does a verified, completed booking exist")
class BookingVerificationController {

    private final VerifyBooking verifyBooking;

    BookingVerificationController(VerifyBooking verifyBooking) {
        this.verifyBooking = verifyBooking;
    }

    @GetMapping
    @Operation(summary = "Verify booking", description = "Does a COMPLETED booking exist for this traveler/trip pair.")
    VerifyBookingResponse verify(@RequestParam UUID travelerId, @RequestParam UUID tripId) {
        VerifyBooking.Result result = verifyBooking.verify(new VerifyBooking.Command(travelerId, new TripId(tripId)));
        return new VerifyBookingResponse(result.verified());
    }
}
