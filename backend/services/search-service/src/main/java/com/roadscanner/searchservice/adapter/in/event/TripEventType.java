package com.roadscanner.searchservice.adapter.in.event;

/**
 * The discriminator carried by every message on the trip-events topic
 * (docs/services/search-service/boundaries.md's "Ordering Edge Case": all three of
 * {@code operator-service}'s trip lifecycle events share one topic, partitioned by trip id, so
 * that ordering between them for the same trip is guaranteed).
 */
public enum TripEventType {
    PUBLISHED,
    UPDATED,
    CANCELLED
}
