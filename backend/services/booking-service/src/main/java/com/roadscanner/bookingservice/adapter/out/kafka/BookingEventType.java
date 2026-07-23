package com.roadscanner.bookingservice.adapter.out.kafka;

/** The discriminator on the {@code booking-events} topic
 * (docs/services/booking-service/events-published.md). */
public enum BookingEventType {
    CREATED,
    CONFIRMED,
    CANCELLED
}
