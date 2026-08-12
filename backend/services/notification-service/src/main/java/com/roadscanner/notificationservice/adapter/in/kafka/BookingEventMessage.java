package com.roadscanner.notificationservice.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code booking-events}' wire shape as this service reads it.
 *
 * <p>A local copy rather than a shared type: {@code backend/shared-libs} holds no buildable module,
 * and a consumer that compiled against the producer's class would couple the two services'
 * deployments together — the coupling the event bus exists to avoid.
 *
 * <p>{@code ignoreUnknown}: booking-service may add fields, and none of them should stop a
 * notification going out.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingEventMessage(
        String eventType,
        UUID bookingId,
        UUID travelerId,
        UUID tripId,
        String status,
        String cancellationReason,
        Instant occurredAt,
        UUID eventId,
        String bookingReference,
        String contactEmail,
        String contactPhone,
        String communicationPreference,
        Instant departureTime,
        BigDecimal fareAmount,
        String fareCurrency) {
}
