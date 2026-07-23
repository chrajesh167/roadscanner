package com.roadscanner.bookingservice.adapter.in.rest.hold;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record HoldSeatsRequest(@NotNull UUID tripId, @NotEmpty List<@NotBlank String> seatNumbers) {
}
