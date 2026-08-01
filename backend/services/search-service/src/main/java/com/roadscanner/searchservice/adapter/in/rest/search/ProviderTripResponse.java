package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A live provider trip on the wire.
 *
 * <p>Transport-neutral and provider-neutral: {@code providerCode} names who answered, and nothing
 * else in this shape assumes a bus, a train or an airline. A caller renders every provider's trips
 * identically.
 *
 * <p>{@code boardingPointId}/{@code alightingPointId} are opaque provider references a later
 * booking flow will need; search is the only moment a provider supplies them. Null when the
 * provider does not report them.
 */
@Schema(name = "ProviderTrip", description = "A live trip from an external provider")
public record ProviderTripResponse(
        @Schema(description = "Which provider answered", example = "FLIXBUS") String providerCode,
        @Schema(description = "The provider's own id for this trip") String providerTripId,
        String operatorName,
        String origin,
        String destination,
        Instant departureTime,
        Instant arrivalTime,
        @Schema(description = "Advertised service tier, when the provider reports one",
                example = "AC Sleeper") String serviceClass,
        BigDecimal fareAmount,
        String fareCurrency,
        @Schema(description = "As reported at search time — a hint, re-validated at hold time")
        int seatsAvailable,
        String boardingPointId,
        String alightingPointId
) {

    public static ProviderTripResponse from(ProviderTripResult trip) {
        return new ProviderTripResponse(
                trip.providerCode(),
                trip.providerTripId(),
                trip.operatorName(),
                trip.route().origin(),
                trip.route().destination(),
                trip.schedule().departureTime(),
                trip.schedule().arrivalTime(),
                trip.serviceClass(),
                trip.fare().amount(),
                trip.fare().currency().getCurrencyCode(),
                trip.seatsAvailable(),
                trip.boardingPointId(),
                trip.alightingPointId());
    }
}
