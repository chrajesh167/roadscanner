package com.roadscanner.paymentservice.adapter.in.event;

/** The discriminator {@code booking-service} publishes on its {@code booking-events} topic
 * ({@code booking-service}'s {@code adapter.out.kafka.BookingEventType}). {@code payment-service}
 * consumes all three but acts only on {@code CANCELLED}, for reconciliation
 * (docs/services/payment-service/events-consumed.md). Declaring all three keeps a {@code CREATED}
 * or {@code CONFIRMED} delivery from failing deserialization. */
public enum BookingEventType {
    CREATED,
    CONFIRMED,
    CANCELLED
}
