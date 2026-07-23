package com.roadscanner.bookingservice.adapter.in.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The wire shape of a message on {@code inventory-service}'s merged-catalog trip-events topic —
 * <strong>deliberately field-for-field identical</strong> to that service's own
 * {@code CatalogTripEventMessage} (docs/services/inventory-service/events-published.md), the same
 * "no translation needed at the boundary" discipline already established platform-wide. This
 * service only reacts to {@code eventType = CANCELLED}
 * (docs/services/booking-service/events-consumed.md) — every other field exists solely so this
 * type can deserialize the full envelope without error.
 */
public record CatalogTripEventMessage(
        CatalogTripEventType eventType,
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
