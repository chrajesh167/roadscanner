package com.roadscanner.providerintegrationservice.adapter.in.rest.search;

import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One normalized trip on the wire. Provider-neutral by construction: a caller cannot tell which
 * provider answered except by reading {@code providerType}, and no provider payload, URL or
 * response model appears here.
 *
 * <p>{@code boardingPointId}/{@code alightingPointId} are opaque provider references carried through
 * for later booking, which needs them and has no other moment to obtain them. Named for the
 * concept rather than one transport mode's word for it — a bus stop, rail station, airport and
 * ferry terminal are all boarding points. Null when the provider does not report them separately
 * from cities.
 */
public record ProviderTripResponse(String providerTripId, String providerType, String operatorName, String origin,
                                    String destination, Instant departureTime, Instant arrivalTime, String serviceClass,
                                    BigDecimal fareAmount, String fareCurrency, int seatsAvailable,
                                    String boardingPointId, String alightingPointId) {

    public static ProviderTripResponse from(ProviderTrip trip) {
        return new ProviderTripResponse(trip.providerTripId(), trip.providerType().code(), trip.operatorName(),
                trip.origin(), trip.destination(), trip.departureTime(), trip.arrivalTime(), trip.serviceClass(),
                trip.fare().amount(), trip.fare().currency().getCurrencyCode(), trip.seatsAvailable(),
                trip.boardingPointId(), trip.alightingPointId());
    }
}
