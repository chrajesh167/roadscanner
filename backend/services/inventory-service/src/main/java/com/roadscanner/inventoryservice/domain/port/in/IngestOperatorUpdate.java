package com.roadscanner.inventoryservice.domain.port.in;

import java.util.Objects;
import java.util.UUID;

/** Ingests an {@code OperatorUpdated} event from {@code operator-service} — refreshes the
 * denormalized {@code OperatorRef} display name and every affected {@code Trip}'s copy of it,
 * then re-publishes {@code OperatorUpdated} on this service's own catalog topic
 * (docs/services/inventory-service/use-cases.md). */
public interface IngestOperatorUpdate {

    void ingest(Command command);

    record Command(UUID operatorId, String displayName) {
        public Command {
            Objects.requireNonNull(operatorId, "operatorId must not be null");
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
        }
    }
}
