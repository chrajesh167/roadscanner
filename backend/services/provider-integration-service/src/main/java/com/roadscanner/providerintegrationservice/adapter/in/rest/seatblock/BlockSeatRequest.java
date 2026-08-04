package com.roadscanner.providerintegrationservice.adapter.in.rest.seatblock;

import com.roadscanner.providerintegrationservice.adapter.in.rest.booking.BlockPassengerRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Who is being held a seat, not merely which seats.
 *
 * <p>A hold binds a seat to its occupant: providers need the traveller to honour gender-restricted
 * seats, and a bare list of seat numbers cannot express that. Carrying the seat on each passenger
 * also means the two can never fall out of step the way parallel lists do.
 */
public record BlockSeatRequest(@NotEmpty List<@Valid BlockPassengerRequest> passengers) {
}
