package com.roadscanner.bookingservice.adapter.in.event;

/** Field-for-field identical to {@code inventory-service}'s own {@code CatalogTripEventType} —
 * this service only ever acts on {@code CANCELLED}, but must still be able to deserialize
 * {@code PUBLISHED}/{@code UPDATED} messages on the same topic without error
 * (docs/services/booking-service/events-consumed.md). */
public enum CatalogTripEventType {
    PUBLISHED,
    UPDATED,
    CANCELLED
}
