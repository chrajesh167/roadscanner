package com.roadscanner.searchservice.adapter.in.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The wire shape of a {@code ReviewSubmitted} message. Carries {@code review-service}'s
 * already-computed aggregate (average, review count) — never review text or reviewer identity,
 * consistent with docs/services/search-service/domain-model.md: this service holds only the
 * denormalized aggregate, and never recomputes an average itself
 * (docs/services/search-service/boundaries.md, "Relationship to review-service").
 */
public record ReviewSubmittedMessage(
        UUID tripId,
        double ratingAverage,
        int ratingReviewCount,
        Instant occurredAt
) {
}
