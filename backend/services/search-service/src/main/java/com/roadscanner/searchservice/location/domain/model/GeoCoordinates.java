package com.roadscanner.searchservice.location.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A latitude/longitude pair. Optional on a {@link Location} — a curated city entry may exist
 * before anyone has pinned its exact point — but never half-present: a lone latitude is not a
 * coordinate, which is why this is one value object rather than two nullable fields, and why the
 * V2 migration carries a matching paired-or-null check constraint.
 *
 * <p>Scale is fixed at 7 decimal places to match {@code DECIMAL(10,7)} in the schema — roughly
 * 1cm of precision, far beyond what a bus stop needs, and enough that equality here means
 * equality in the database rather than "equal until Hibernate rounds it".
 */
public record GeoCoordinates(BigDecimal latitude, BigDecimal longitude) {

    private static final int SCALE = 7;
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    public GeoCoordinates {
        Objects.requireNonNull(latitude, "latitude must not be null");
        Objects.requireNonNull(longitude, "longitude must not be null");

        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }

        latitude = latitude.setScale(SCALE, RoundingMode.HALF_UP);
        longitude = longitude.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Both present, or neither — anything else is a caller bug, not a valid partial coordinate. */
    public static GeoCoordinates ofNullable(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null && longitude == null) {
            return null;
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("latitude and longitude must be provided together");
        }
        return new GeoCoordinates(latitude, longitude);
    }

    @Override
    public String toString() {
        return latitude + "," + longitude;
    }
}
