package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.port.in.ConfirmBooking;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Internal-only — converts a still-blocked seat hold into a confirmed booking with the provider. */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/sessions/{sessionId}/seat-blocks/{providerBlockReference}/booking")
@Tag(name = "Provider Bookings", description = "Confirm a booking against a seat block")
class ProviderBookingController {

    private final ConfirmBooking confirmBooking;

    ProviderBookingController(ConfirmBooking confirmBooking) {
        this.confirmBooking = confirmBooking;
    }

    @PostMapping
    @Operation(summary = "Confirm a booking", description = "Converts a still-blocked seat hold into a confirmed booking.")
    BookingConfirmationResponse confirm(@PathVariable String providerType, @PathVariable UUID sessionId,
                                         @PathVariable String providerBlockReference,
                                         @Valid @RequestBody ConfirmBookingRequest request) {
        List<PassengerDetail> passengers = request.passengers().stream().map(PassengerRequest::toDomain).toList();
        ConfirmBooking.Result result = confirmBooking.confirm(new ConfirmBooking.Command(
                new ProviderSessionId(sessionId), providerBlockReference, request.providerTripId(), passengers));
        return BookingConfirmationResponse.from(result.confirmation());
    }
}
