package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One trip as a provider reported it, normalized into RoadScanner's own vocabulary.
 *
 * <p>This is the canonical shape every provider's response becomes. A caller receives an identical
 * record whether the trip came from FlixBus, RedBus or a rail operator — which is what lets
 * search-service stay provider-agnostic while still federating provider results.
 *
 * <p><strong>The three identifier fields exist for later booking, not for search.</strong> A
 * provider needs its own references handed back to block a seat or confirm a booking, and the only
 * moment they are available is the search response. They are opaque here: this service stores and
 * returns them, and never parses them.
 *
 * <p><strong>Transport-neutral.</strong> Nothing here assumes a bus. A rail, airline or ferry
 * adapter populates the same record without inventing placeholder values, which is the test that
 * matters: a model forcing {@code "N/A"} or {@code "Coach"} into a field is a model that will need
 * redesigning the first time a second transport mode arrives.
 *
 * <p>{@code serviceClass} replaces what was {@code busType}. It is the fare/comfort tier a provider
 * advertises — "AC Sleeper" for a coach, "First Class" for rail, "Economy" for a flight — and is
 * <em>optional</em>, because a provider that does not classify its service must be able to say so
 * rather than be forced to lie. Making it mandatory would recreate the original defect in a new
 * name.
 *
 * <p>{@code boardingPointId}/{@code alightingPointId} replace station-specific naming. A bus stop,
 * a rail station, an airport gate and a ferry terminal are all boarding points; naming the concept
 * after one mode would leak that mode into every other adapter.
 *
 * @param providerTripId     the provider's id for this trip — its ride/journey/flight reference
 * @param operatorName       the carrier as the provider names it, for display
 * @param serviceClass       the advertised service tier, when the provider reports one
 * @param boardingPointId    the provider's id for where the traveller boards, when reported
 * @param alightingPointId   the provider's id for where the traveller alights, when reported
 * @param seatsAvailable     as reported at search time; a hint, never a guarantee — seat state is
 *                           re-validated live at hold time. Generic across modes: coaches, trains,
 *                           aircraft and ferries all sell seats
 */
public record ProviderTrip(String providerTripId, ProviderType providerType, String operatorName, String origin,
                            String destination, Instant departureTime, Instant arrivalTime, String serviceClass,
                            FareAmount fare, int seatsAvailable, String boardingPointId, String alightingPointId) {

    public ProviderTrip {
        if (providerTripId == null || providerTripId.isBlank()) {
            throw new IllegalArgumentException("providerTripId must not be blank");
        }
        Objects.requireNonNull(providerType, "providerType must not be null");
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("operatorName must not be blank");
        }
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("origin must not be blank");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("arrivalTime must be after departureTime");
        }
        Objects.requireNonNull(fare, "fare must not be null");
        if (seatsAvailable < 0) {
            throw new IllegalArgumentException("seatsAvailable must not be negative");
        }
        serviceClass = blankToNull(serviceClass);
        boardingPointId = blankToNull(boardingPointId);
        alightingPointId = blankToNull(alightingPointId);
    }

    /** Absent when the provider does not classify its service — never a placeholder. */
    public Optional<String> serviceClassIfPresent() {
        return Optional.ofNullable(serviceClass);
    }

    public Optional<String> boardingPointIdIfPresent() {
        return Optional.ofNullable(boardingPointId);
    }

    public Optional<String> alightingPointIdIfPresent() {
        return Optional.ofNullable(alightingPointId);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
