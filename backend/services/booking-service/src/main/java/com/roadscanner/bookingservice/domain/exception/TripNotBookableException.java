package com.roadscanner.bookingservice.domain.exception;

import com.roadscanner.bookingservice.domain.model.TripId;

/** Thrown by {@code Hold Seats} when {@code inventory-service} reports the trip doesn't exist,
 * is no longer bookable ({@code TripCancelled}), or — the documented, unclosed platform gap this
 * exception exists to surface clearly — has no {@code ProviderMapping} at all
 * (docs/services/booking-service/use-cases.md's "A Trip With No `ProviderMapping` Cannot Be
 * Held"). */
public class TripNotBookableException extends BookingServiceException {

    private final TripId tripId;

    public TripNotBookableException(TripId tripId, String reason) {
        super("Trip " + tripId + " cannot currently be booked: " + reason);
        this.tripId = tripId;
    }

    public TripId tripId() {
        return tripId;
    }
}
