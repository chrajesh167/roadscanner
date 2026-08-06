package com.roadscanner.bookingservice.adapter.in.rest.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Passengers are not accepted here: they were bound to their seats when the hold was placed, and
 * taking them again would invite a booking whose travellers disagree with the ones the seats are
 * actually held for. They are read from the hold instead. What this step adds is the contact.
 */
public record CreateBookingRequest(@NotNull UUID seatHoldId, @NotNull @Valid ContactRequest contact) {
}
