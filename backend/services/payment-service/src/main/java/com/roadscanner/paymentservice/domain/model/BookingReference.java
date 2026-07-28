package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * An opaque pointer to a {@code booking-service} {@code Booking} — the booking this payment is for.
 * {@code payment-service} <strong>never</strong> dereferences it against {@code booking-service}'s
 * database (that would violate docs/architecture/database-ownership.md); it uses it only to key
 * events and correlate webhooks and refund requests. The exact mirror of {@code booking-service}'s
 * own opaque {@code paymentReference} into this service — each side holds an opaque reference to
 * the other's data, neither reads the other's tables
 * (docs/services/payment-service/data-ownership.md). Wraps the booking's {@link UUID}, so the
 * {@code bookingId} field {@code booking-service}'s frozen payment-events consumer expects is
 * exactly this value (docs/services/booking-service/events-consumed.md).
 */
public record BookingReference(UUID value) {

    public BookingReference {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
