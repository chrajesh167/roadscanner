package com.roadscanner.providerintegrationservice.adapter.in.rest.seatblock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BlockSeatRequest(@NotEmpty List<@NotBlank String> seatNumbers) {
}
