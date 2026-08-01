package com.roadscanner.providerintegrationservice.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * What to search a provider for, expressed in that provider's own place identifiers.
 *
 * <p>Deliberately <strong>not</strong> free-text place names. Providers search by an opaque id they
 * issued, not by a name they would have to resolve, so accepting "Hyderabad" here would force every
 * adapter to invent its own name-to-id lookup — and the platform would end up with as many
 * location-resolution schemes as it has providers.
 *
 * <p>The ids are opaque to this service: it never parses, validates or interprets them, and their
 * meaning belongs entirely to the provider that issued them. Translation from a canonical
 * RoadScanner {@code LocationId} happens once, in search-service, against
 * {@code provider_location_mapping} — the platform's single mapping table.
 *
 * @param originCityId      the provider's own id for the departure city
 * @param destinationCityId the provider's own id for the arrival city
 */
public record SearchCriteria(String originCityId, String destinationCityId, LocalDate travelDate) {

    public SearchCriteria {
        originCityId = requireNonBlank(originCityId, "originCityId");
        destinationCityId = requireNonBlank(destinationCityId, "destinationCityId");
        // Case-sensitive: these are opaque provider ids, and nothing here may assume that an id's
        // casing is insignificant to the system that issued it.
        if (originCityId.equals(destinationCityId)) {
            throw new IllegalArgumentException("originCityId and destinationCityId must differ");
        }
        Objects.requireNonNull(travelDate, "travelDate must not be null");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
