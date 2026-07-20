package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmBookingRequest(@NotBlank String providerTripId, @NotEmpty List<@Valid PassengerRequest> passengers) {
}
