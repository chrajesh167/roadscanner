package com.roadscanner.inventoryservice.domain.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ingests a first-party {@code TripPublished} event from {@code operator-service}
 * (docs/services/inventory-service/use-cases.md). Per
 * docs/services/inventory-service/boundaries.md's "how a trip instance gets its seat map"
 * decision, the event payload carries a denormalized <strong>static</strong> seat-layout
 * snapshot — {@code seatLayout} here — so this service never calls back to
 * {@code operator-service} synchronously. Every entry is shape-only (no status field exists to
 * carry, matching {@link com.roadscanner.inventoryservice.domain.model.Seat}).
 */
public interface IngestPublishedTrip {

    void ingest(Command command);

    record Command(UUID tripId, UUID operatorId, String operatorName, String origin, String destination,
                    Instant departureTime, Instant arrivalTime, String busTypeCategory, List<String> amenities,
                    BigDecimal fareAmount, String fareCurrency, List<SeatEntry> seatLayout, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(operatorId, "operatorId must not be null");
            Objects.requireNonNull(operatorName, "operatorName must not be null");
            Objects.requireNonNull(origin, "origin must not be null");
            Objects.requireNonNull(destination, "destination must not be null");
            Objects.requireNonNull(departureTime, "departureTime must not be null");
            Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
            Objects.requireNonNull(busTypeCategory, "busTypeCategory must not be null");
            amenities = amenities == null ? List.of() : List.copyOf(amenities);
            Objects.requireNonNull(fareAmount, "fareAmount must not be null");
            Objects.requireNonNull(fareCurrency, "fareCurrency must not be null");
            if (seatLayout == null || seatLayout.isEmpty()) {
                throw new IllegalArgumentException("seatLayout must not be empty");
            }
            seatLayout = List.copyOf(seatLayout);
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }

    /** One static seat entry, exactly as {@code operator-service} denormalizes it into the
     * event — no status field, matching {@code Seat}'s Javadoc. */
    record SeatEntry(String seatNumber, String deck, String seatType, boolean wheelchairAccessible, Integer position) {
        public SeatEntry {
            Objects.requireNonNull(seatNumber, "seatNumber must not be null");
            Objects.requireNonNull(deck, "deck must not be null");
            Objects.requireNonNull(seatType, "seatType must not be null");
        }
    }
}
