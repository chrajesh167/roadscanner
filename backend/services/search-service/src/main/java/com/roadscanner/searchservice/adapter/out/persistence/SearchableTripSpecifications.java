package com.roadscanner.searchservice.adapter.out.persistence;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the dynamic filter query behind {@code SearchableTripRepository.search(SearchQuery)}
 * from primitive/JPA-safe values only — no {@code domain.model} import, ever (see
 * {@link SearchableTripMapper}'s Javadoc for the line this class stays on the other side of).
 * A {@link Specification} per optional filter, composed with {@code and}, is what lets a query
 * with five optional filters avoid five near-duplicate {@code @Query} methods for every
 * combination of present/absent filters — the standard Spring Data JPA pattern for this exact
 * problem.
 */
final class SearchableTripSpecifications {

    private SearchableTripSpecifications() {
    }

    static Specification<SearchableTripJpaEntity> bookable() {
        return (root, query, cb) -> cb.isTrue(root.get("bookable"));
    }

    static Specification<SearchableTripJpaEntity> route(String origin, String destination) {
        return (root, query, cb) -> cb.and(
                cb.equal(cb.lower(root.get("origin")), origin.toLowerCase()),
                cb.equal(cb.lower(root.get("destination")), destination.toLowerCase()));
    }

    /**
     * Departure-time window for a calendar travel date. Filters against the day boundary in
     * UTC — an explicit, documented simplification (the platform-wide timezone/locale policy
     * this should ultimately follow is not yet specified anywhere in
     * docs/requirements/non-functional-requirements.md NFR-22's terms, so this is an
     * implementation decision, not an architecture one, matching how
     * docs/services/search-service/boundaries.md defers similarly unspecified specifics).
     */
    static Specification<SearchableTripJpaEntity> departingOn(Instant dayStartUtc, Instant dayEndUtc) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("departureTime"), dayStartUtc),
                cb.lessThan(root.get("departureTime"), dayEndUtc));
    }

    static Specification<SearchableTripJpaEntity> minFare(BigDecimal minFare) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fareAmount"), minFare);
    }

    static Specification<SearchableTripJpaEntity> maxFare(BigDecimal maxFare) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fareAmount"), maxFare);
    }

    static Specification<SearchableTripJpaEntity> departureAfter(Instant instant) {
        return (root, query, cb) -> cb.greaterThan(root.get("departureTime"), instant);
    }

    static Specification<SearchableTripJpaEntity> departureBefore(Instant instant) {
        return (root, query, cb) -> cb.lessThan(root.get("departureTime"), instant);
    }

    static Specification<SearchableTripJpaEntity> busTypeCategory(String category) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("busTypeCategory")), category.toLowerCase());
    }

    static Specification<SearchableTripJpaEntity> minRating(double minRating) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ratingAverage"), minRating);
    }

    /** Combines every supplied specification with AND — {@code null} entries are skipped, so
     * callers can build the list unconditionally and let absent filters fall out naturally. */
    static Specification<SearchableTripJpaEntity> allOf(List<Specification<SearchableTripJpaEntity>> specifications) {
        List<Specification<SearchableTripJpaEntity>> present = new ArrayList<>();
        for (Specification<SearchableTripJpaEntity> spec : specifications) {
            if (spec != null) {
                present.add(spec);
            }
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = present.stream()
                    .map(spec -> spec.toPredicate(root, query, cb))
                    .toList();
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
