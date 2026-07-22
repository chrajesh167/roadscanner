package com.roadscanner.inventoryservice.adapter.in.event;

/** The discriminator carried by every message on {@code operator-service}'s trip-events topic —
 * matching {@code search-service}'s identical {@code TripEventType} precedent for the same
 * upstream events. */
public enum OperatorTripEventType {
    PUBLISHED,
    UPDATED,
    CANCELLED
}
