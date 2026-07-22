package com.roadscanner.inventoryservice.domain.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Ingests a {@code TripUpdated} event from {@code operator-service}
 * (docs/services/inventory-service/use-cases.md). */
public interface IngestUpdatedTrip {

    void ingest(Command command);

    record Command(UUID tripId, String origin, String destination, Instant departureTime, Instant arrivalTime,
                    String operatorName, String busTypeCategory, List<String> amenities, BigDecimal fareAmount,
                    String fareCurrency, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(origin, "origin must not be null");
            Objects.requireNonNull(destination, "destination must not be null");
            Objects.requireNonNull(departureTime, "departureTime must not be null");
            Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
            Objects.requireNonNull(operatorName, "operatorName must not be null");
            Objects.requireNonNull(busTypeCategory, "busTypeCategory must not be null");
            amenities = amenities == null ? List.of() : List.copyOf(amenities);
            Objects.requireNonNull(fareAmount, "fareAmount must not be null");
            Objects.requireNonNull(fareCurrency, "fareCurrency must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
