package com.roadscanner.searchservice.domain.model;

/**
 * A denormalized aggregate rating — average and review count only, never review text or
 * reviewer identity (docs/services/search-service/domain-model.md). {@code review-service} owns
 * the average-calculation business logic; this value object only carries the already-computed
 * result that service's {@code ReviewSubmitted} event supplies — {@code search-service} never
 * recomputes an average itself, consistent with docs/services/search-service/boundaries.md's
 * "Relationship to review-service."
 */
public record RatingSnapshot(double average, int reviewCount) {

    private static final RatingSnapshot NONE = new RatingSnapshot(0.0, 0);

    public RatingSnapshot {
        if (average < 0.0 || average > 5.0) {
            throw new IllegalArgumentException("average must be between 0.0 and 5.0");
        }
        if (reviewCount < 0) {
            throw new IllegalArgumentException("reviewCount must not be negative");
        }
    }

    /** A trip with no reviews yet — the state every newly published trip starts in. */
    public static RatingSnapshot none() {
        return NONE;
    }
}
