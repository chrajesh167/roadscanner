package com.roadscanner.searchservice.location.domain.model;

import java.util.Objects;

/**
 * Google's opaque identifier for a place, when one is known.
 *
 * <p>Sprint 1 stores it and enforces uniqueness; it does not call Google. Modelling it as a value
 * object now — rather than a bare {@code String} — is what lets Sprint 2 attach fetch/refresh
 * behaviour and a format rule here without touching {@link Location} or any public DTO.
 *
 * <p>It is deliberately <em>not</em> an identity: {@link LocationId} is. A place can exist here
 * with no Google counterpart, and a Google place that is later re-issued must not silently
 * repoint an existing location.
 */
public record GooglePlaceId(String value) {

    private static final int MAX_LENGTH = 255;

    public GooglePlaceId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("googlePlaceId must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("googlePlaceId must be at most " + MAX_LENGTH + " characters");
        }
    }

    /** Blank and null both mean "no Google place known" — normalise to a single representation. */
    public static GooglePlaceId ofNullable(String value) {
        return (value == null || value.isBlank()) ? null : new GooglePlaceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
