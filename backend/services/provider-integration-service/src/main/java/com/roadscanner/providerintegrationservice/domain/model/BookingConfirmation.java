package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The result of a {@code ConfirmBooking} call. Not persisted here — {@code booking-service}
 * owns the booking record itself (per this service's explicit "does not own" list); this is the
 * provider's confirmation, returned once and not retained. */
public record BookingConfirmation(BookingReference bookingReference, ReservationId reservationId,
                                   String providerTripId, List<PassengerDetail> passengers, FareAmount totalFare,
                                   Instant confirmedAt) {

    public BookingConfirmation {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        if (providerTripId == null || providerTripId.isBlank()) {
            throw new IllegalArgumentException("providerTripId must not be blank");
        }
        if (passengers == null || passengers.isEmpty()) {
            throw new IllegalArgumentException("passengers must not be empty");
        }
        passengers = List.copyOf(passengers);
        Objects.requireNonNull(totalFare, "totalFare must not be null");
        Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
    }
}
