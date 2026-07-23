package com.roadscanner.bookingservice.adapter.out.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * The wire shape published to {@code booking-events} — single topic, discriminated by
 * {@code eventType}, keyed by {@code bookingId}, matching the single-topic-per-domain convention
 * {@code inventory-service}'s {@code CatalogTripEventMessage} and
 * {@code provider-integration-service}'s {@code ProviderAuditMessage} already establish
 * (docs/services/booking-service/events-published.md). {@code cancellationReason} is {@code null}
 * unless {@code eventType = CANCELLED}.
 */
public record BookingEventMessage(BookingEventType eventType, UUID bookingId, UUID travelerId, UUID tripId,
                                   String status, String cancellationReason, Instant occurredAt) {
}
