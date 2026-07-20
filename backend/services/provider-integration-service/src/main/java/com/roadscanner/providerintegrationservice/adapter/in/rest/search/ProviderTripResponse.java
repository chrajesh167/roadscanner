package com.roadscanner.providerintegrationservice.adapter.in.rest.search;

import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderTripResponse(String providerTripId, String providerType, String operatorName, String origin,
                                    String destination, Instant departureTime, Instant arrivalTime, String busType,
                                    BigDecimal fareAmount, String fareCurrency, int seatsAvailable) {

    public static ProviderTripResponse from(ProviderTrip trip) {
        return new ProviderTripResponse(trip.providerTripId(), trip.providerType().code(), trip.operatorName(),
                trip.origin(), trip.destination(), trip.departureTime(), trip.arrivalTime(), trip.busType(),
                trip.fare().amount(), trip.fare().currency().getCurrencyCode(), trip.seatsAvailable());
    }
}
