package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripSearchResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The client-facing shape of one trip — used identically by the search-results list and the
 * trip-detail lookup (docs/services/search-service/use-cases.md: "Live availability lookup is
 * not modeled as its own use case"), so this DTO exists exactly once rather than duplicated per
 * endpoint. {@code availableSeats} is {@code null} exactly when {@code availabilityKnown} is
 * {@code false} — the client-facing rendering of {@link AvailabilityStatus#unknown()}
 * (docs/services/search-service/boundaries.md's "degrade, not fail" rule).
 */
public record TripResponse(
        String tripId,
        String operatorId,
        String operatorName,
        String origin,
        String destination,
        Instant departureTime,
        Instant arrivalTime,
        long durationMinutes,
        String busTypeCategory,
        List<String> amenities,
        BigDecimal fareAmount,
        String fareCurrency,
        boolean bookable,
        double ratingAverage,
        int ratingReviewCount,
        Integer availableSeats,
        boolean availabilityKnown
) {

    public static TripResponse from(TripSearchResult result) {
        SearchableTrip trip = result.trip();
        AvailabilityStatus availability = result.availability();
        return new TripResponse(
                trip.tripId().toString(),
                trip.operatorId().toString(),
                trip.operatorName(),
                trip.route().origin(),
                trip.route().destination(),
                trip.schedule().departureTime(),
                trip.schedule().arrivalTime(),
                trip.schedule().duration().toMinutes(),
                trip.busType().category(),
                trip.busType().amenities(),
                trip.fare().amount(),
                trip.fare().currency().getCurrencyCode(),
                trip.bookable(),
                trip.rating().average(),
                trip.rating().reviewCount(),
                availability.isKnown() ? availability.seatsAvailable().getAsInt() : null,
                availability.isKnown()
        );
    }
}
