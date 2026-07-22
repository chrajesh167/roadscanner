package com.roadscanner.inventoryservice.adapter.out.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The wire shape published to the merged-catalog trip-events topic — <strong>field-for-field
 * identical</strong> to {@code search-service}'s already-shipped {@code TripEventMessage}
 * (same names, same types, same JSON shape), by design: per
 * docs/services/inventory-service/events-published.md, this is what makes a future topic-source
 * config swap on {@code search-service}'s side a zero-code-change operation. Do not add, remove,
 * rename, or reorder fields here without updating that document's claim.
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
