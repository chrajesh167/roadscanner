package com.roadscanner.searchservice.domain.exception;

import com.roadscanner.searchservice.domain.model.TripId;

/**
 * A trip-detail lookup ({@code GetTripDetail}) referenced a {@link TripId} not present in this
 * service's index. This can legitimately mean the trip was never published, was cancelled and
 * later purged, or {@code search-service} is simply behind on the {@code TripPublished} event
 * for it (docs/services/search-service/events-consumed.md) — the client-facing response does
 * not need to (and cannot) distinguish these, so this exception carries no further detail than
 * the id itself.
 */
public final class TripNotFoundException extends SearchServiceException {

    private final TripId tripId;

    public TripNotFoundException(TripId tripId) {
        super("Trip not found: " + tripId);
        this.tripId = tripId;
    }

    public TripId tripId() {
        return tripId;
    }
}
