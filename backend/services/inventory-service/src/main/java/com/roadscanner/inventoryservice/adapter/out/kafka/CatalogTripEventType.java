package com.roadscanner.inventoryservice.adapter.out.kafka;

/** Field-for-field identical to {@code search-service}'s own {@code TripEventType} — deliberate,
 * so a future switch of that service's consumer from {@code operator-service}'s topic to this
 * one is a config change, not a code change (docs/services/inventory-service/events-published.md). */
public enum CatalogTripEventType {
    PUBLISHED,
    UPDATED,
    CANCELLED
}
