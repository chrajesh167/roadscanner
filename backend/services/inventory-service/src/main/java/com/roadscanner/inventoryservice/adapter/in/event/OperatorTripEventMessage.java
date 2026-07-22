package com.roadscanner.inventoryservice.adapter.in.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The wire shape of a message on {@code operator-service}'s trip-events topic — one envelope for
 * all three of {@link OperatorTripEventType}'s values, matching {@code search-service}'s
 * {@code TripEventMessage} precedent. {@code seatLayout} is present (non-empty) only on
 * {@code PUBLISHED}; fields beyond {@code eventType}/{@code tripId}/{@code occurredAt} are only
 * meaningful for {@code PUBLISHED}/{@code UPDATED} and may be absent on {@code CANCELLED} —
 * deliberately not validated here for the same reason {@code search-service}'s identical envelope
 * isn't: a single envelope type cannot enforce two different required-field sets at once, so
 * validation happens one level down, constructing the relevant inbound port's command record.
 */
public record OperatorTripEventMessage(
        OperatorTripEventType eventType,
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
        List<SeatEntryMessage> seatLayout,
        Instant occurredAt
) {
}
