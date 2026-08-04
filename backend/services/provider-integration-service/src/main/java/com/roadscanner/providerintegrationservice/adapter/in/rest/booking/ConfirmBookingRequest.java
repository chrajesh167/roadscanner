package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The trip is not accepted from the caller: it is read from the stored hold the block reference
 * identifies. A caller-supplied trip id could disagree with the departure the seats were actually
 * held against, and the provider would confirm regardless.
 */
public record ConfirmBookingRequest(@NotNull @Valid ContactRequest contact,
                                    @NotEmpty List<@Valid PassengerRequest> passengers) {
}
