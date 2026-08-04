package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Objects;

/**
 * One seat, bound to the provider-side identifiers that later calls in a booking flow require.
 *
 * <p>Providers routinely use three different handles for the same seat: the label a traveller sees
 * ("12"), an opaque id for the physical seat on a specific departure, and a per-passenger ticket
 * handle the seat is attached to. Reserving, confirming and cancelling each need a different one,
 * and the mapping between them exists only in the response that issued them — so it is captured
 * here at the moment it is known and carried through the flow.
 *
 * <p>Losing either id means the reservation cannot be completed and cannot be cancelled: there is
 * no lookup that recovers a provider's internal id from a seat label after the fact.
 *
 * @param seatNumber       the label a traveller recognises
 * @param providerSeatId   the provider's own id for this seat on this departure, opaque here
 * @param providerTicketId the provider's ticket handle this seat is attached to, opaque here
 */
public record SeatAssignment(SeatNumber seatNumber, String providerSeatId, String providerTicketId) {

    public SeatAssignment {
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        providerSeatId = requireNonBlank(providerSeatId, "providerSeatId");
        providerTicketId = requireNonBlank(providerTicketId, "providerTicketId");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
