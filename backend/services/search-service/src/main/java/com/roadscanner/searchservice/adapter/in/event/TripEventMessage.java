package com.roadscanner.searchservice.adapter.in.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The wire shape of a message on the trip-events topic — one envelope for all three of
 * {@link TripEventType}'s values, since {@code operator-service} publishes them together,
 * discriminated by {@code eventType} (docs/services/search-service/boundaries.md). Fields
 * beyond {@code eventType}, {@code tripId}, and {@code occurredAt} are only meaningful for
 * {@code PUBLISHED}/{@code UPDATED} and are absent (null) on a {@code CANCELLED} message —
 * deliberately not validated here, since a single envelope type cannot enforce two different
 * required-field sets at once. Validation happens naturally one level down: constructing the
 * relevant inbound port's command record enforces non-null on exactly the fields that event
 * type actually requires (see {@link TripEventListener}).
 *
 * Exact wire schema is this service's own consumption contract, not a platform-published
 * OpenAPI/Avro schema yet — per docs/architecture/event-catalog.md, "exact payload schemas are
 * not defined... see docs/api/ once services exist."
 */
public record TripEventMessage(
        TripEventType eventType,
        UUID tripId,
        UUID operatorId,
        String operatorName,
        String origin,
        String destination,
        Instant departureTime,
        Instant arrivalTime,
        String busTypeCategory,
        List<String> amenities,
        BigDecimal fareAmount,
        String fareCurrency,
        Instant occurredAt
) {
}
