package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
 *
 * <p>{@code catalogTripId} is what makes a provider trip actionable. A provider identifies its trip
 * by {@code providerTripId}, but every booking step is keyed by a catalog trip id, so without this
 * a caller could display the trip and do nothing else with it. Null means catalog sync has not
 * imported this departure yet: the trip is real and shown, but nothing can be booked against it —
 * deliberately distinguishable from "bookable", because presenting an unbookable trip as selectable
 * only moves the failure to the next screen.
 *
 * <p>It also carries the identity a caller needs to recognise that an indexed trip in the same
 * response <em>is this same departure</em>, imported earlier, rather than a second bus.
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
        String alightingPointId,
        @Schema(description = "The catalog trip backing this provider trip, when one exists. "
                + "Null when catalog sync has not imported this departure — the trip is real but "
                + "not bookable yet.")
        UUID catalogTripId
) {

    public static ProviderTripResponse from(ProviderTripResult trip, UUID catalogTripId) {
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
                trip.alightingPointId(),
                catalogTripId);
    }
}
