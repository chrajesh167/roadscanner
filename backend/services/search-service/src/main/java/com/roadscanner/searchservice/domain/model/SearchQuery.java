package com.roadscanner.searchservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A traveler's search ask (docs/services/search-service/domain-model.md) — origin/destination/
 * date plus the optional filter and sort parameters FR-2.3 describes, and pagination. A pure
 * input shape: carries no persistence or identity, never stored.
 *
 * Filter fields are plain nullable components, not {@code Optional}-wrapped — this is a
 * criteria object, not an entity with an optional persisted field (contrast
 * {@link com.roadscanner.searchservice.domain.model.SearchableTrip}'s persisted-state
 * accessors); {@code null} simply means "no filter on this dimension," checked directly by
 * whichever layer applies it (docs/services/search-service/use-cases.md).
 *
 * {@code sort} may be {@code null} — resolving "no option specified" to a concrete default is
 * {@link com.roadscanner.searchservice.domain.service.SearchRankingPolicy}'s job, not this
 * record's.
 *
 * The absolute page-size ceiling below is a domain-enforced safety invariant, not the
 * operational default — the *default* and *tunable* maximum (usually lower) are applied by the
 * REST layer, from configuration, before a query ever reaches this constructor, the same
 * "domain enforces the shape, configuration enforces the specific baseline" split
 * {@code auth-service}'s {@code PasswordComplexityPolicy} uses.
 */
public record SearchQuery(
        Route route,
        LocalDate travelDate,
        BigDecimal minFare,
        BigDecimal maxFare,
        Instant departureAfter,
        Instant departureBefore,
        String busTypeCategory,
        Double minRating,
        SortOption sort,
        int page,
        int size
) {
    private static final int ABSOLUTE_MAX_PAGE_SIZE = 100;

    public SearchQuery {
        Objects.requireNonNull(route, "route must not be null");
        Objects.requireNonNull(travelDate, "travelDate must not be null");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > ABSOLUTE_MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + ABSOLUTE_MAX_PAGE_SIZE);
        }
        if (minFare != null && minFare.signum() < 0) {
            throw new IllegalArgumentException("minFare must not be negative");
        }
        if (maxFare != null && maxFare.signum() < 0) {
            throw new IllegalArgumentException("maxFare must not be negative");
        }
        if (minFare != null && maxFare != null && minFare.compareTo(maxFare) > 0) {
            throw new IllegalArgumentException("minFare must not be greater than maxFare");
        }
        if (departureAfter != null && departureBefore != null && !departureAfter.isBefore(departureBefore)) {
            throw new IllegalArgumentException("departureAfter must be before departureBefore");
        }
        if (minRating != null && (minRating < 0.0 || minRating > 5.0)) {
            throw new IllegalArgumentException("minRating must be between 0.0 and 5.0");
        }
        if (busTypeCategory != null) {
            busTypeCategory = busTypeCategory.trim();
            if (busTypeCategory.isEmpty()) {
                busTypeCategory = null;
            }
        }
    }
}
