package com.roadscanner.inventoryservice.domain.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Ingests a {@code TripCancelled} event from {@code operator-service}
 * (docs/services/inventory-service/use-cases.md). */
public interface IngestCancelledTrip {

    void ingest(Command command);

    record Command(UUID tripId, Instant occurredAt) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
