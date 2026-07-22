package com.roadscanner.inventoryservice.adapter.in.rest.trip;

import com.roadscanner.inventoryservice.domain.model.Trip;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TripResponse(String tripId, String origin, String destination, Instant departureTime,
                            Instant arrivalTime, String operatorId, String operatorDisplayName,
                            String busTypeCategory, List<String> amenities, BigDecimal fareAmount,
                            String fareCurrency, boolean bookable, String supplyOrigin) {

    public static TripResponse from(Trip trip) {
        return new TripResponse(trip.id().toString(), trip.origin(), trip.destination(),
                trip.schedule().departureTime(), trip.schedule().arrivalTime(),
                trip.operatorId().map(Object::toString).orElse(null), trip.operatorDisplayName(),
                trip.busTypeCategory(), trip.amenities(), trip.fare().amount(),
                trip.fare().currency().getCurrencyCode(), trip.bookable(), trip.supplyOrigin().name());
    }
}
