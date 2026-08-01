package com.roadscanner.searchservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One trip returned by an external provider, in RoadScanner's own vocabulary.
 *
 * <p>Search-service's view of a provider result. It is deliberately identical in shape whichever
 * provider answered — normalization already happened in provider-integration-service, which is the
 * only service permitted to know a provider's response format. Nothing here is provider-specific:
 * {@code providerCode} names which provider answered, and the boarding/alighting ids are opaque strings
 * this service stores and forwards but never interprets.
 *
 * <p>Transport-neutral: nothing here assumes a bus. {@code serviceClass} is optional because a
 * provider that does not classify its service must be able to say so rather than be made to
 * invent a tier, and boarding/alighting points are named for the concept rather than one mode's
 * word for it.
 *
 * <p>Distinct from {@link SearchableTrip}, which is the indexed first-party read model. A provider
 * result is fetched live per request and never persisted here — it has no lifecycle in this
 * service, so giving it an aggregate's shape would misrepresent it.
 */
public record ProviderTripResult(String providerCode, String providerTripId, String operatorName, Route route,
                                 Schedule schedule, String serviceClass, FareSnapshot fare, int seatsAvailable,
                                 String boardingPointId, String alightingPointId) {

    public ProviderTripResult {
        providerCode = requireNonBlank(providerCode, "providerCode");
        providerTripId = requireNonBlank(providerTripId, "providerTripId");
        operatorName = requireNonBlank(operatorName, "operatorName");
        Objects.requireNonNull(route, "route must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(fare, "fare must not be null");
        if (seatsAvailable < 0) {
            throw new IllegalArgumentException("seatsAvailable must not be negative");
        }
        serviceClass = blankToNull(serviceClass);
        boardingPointId = blankToNull(boardingPointId);
        alightingPointId = blankToNull(alightingPointId);
    }

    public Instant departureTime() {
        return schedule.departureTime();
    }

    /** Absent when the provider does not classify its service — never a placeholder. */
    public Optional<String> serviceClassIfPresent() {
        return Optional.ofNullable(serviceClass);
    }

    /** Opaque provider references, carried for a later booking flow that will need them. */
    public Optional<String> boardingPointIdIfPresent() {
        return Optional.ofNullable(boardingPointId);
    }

    public Optional<String> alightingPointIdIfPresent() {
        return Optional.ofNullable(alightingPointId);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
