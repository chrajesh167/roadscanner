package com.roadscanner.bookingservice.adapter.in.rest.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(@NotNull UUID seatHoldId, @NotEmpty List<@Valid PassengerRequest> passengers) {
}
