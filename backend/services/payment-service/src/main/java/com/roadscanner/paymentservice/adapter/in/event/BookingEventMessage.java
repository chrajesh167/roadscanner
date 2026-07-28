package com.roadscanner.paymentservice.adapter.in.event;

import java.time.Instant;
import java.util.UUID;

/**
 * This service's own copy of the wire shape {@code booking-service} publishes to {@code booking-events}
 * ({@code booking-service}'s {@code adapter.out.kafka.BookingEventMessage}) — an independently
 * maintained DTO, the platform-wide "each consumer owns its copy of the upstream shape" convention
 * (docs/services/payment-service/events-consumed.md). {@code cancellationReason} is present only on
 * {@code CANCELLED}.
 */
public record BookingEventMessage(BookingEventType eventType, UUID bookingId, UUID travelerId, UUID tripId,
                                  String status, String cancellationReason, Instant occurredAt) {
}
