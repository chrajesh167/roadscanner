package com.roadscanner.inventoryservice.domain.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Ingests a {@code RouteUpdated} event from {@code operator-service}
 * (docs/services/inventory-service/use-cases.md). This service's own {@code Route} catalog is
 * administratively managed (docs/services/inventory-service/domain-model.md's summary table —
 * "kept current via: administrative catalog-management, not event-driven"), and no specified
 * mapping exists from {@code operator-service}'s per-operator route/schedule concept to this
 * service's city-to-city {@code Route} entity. This handler is therefore intentionally
 * acknowledgment-only (logged, no reconciliation, no re-publication) — a documented scope
 * simplification, not a silent gap; see the implementation summary delivered with this service.
 */
public interface IngestRouteUpdate {

    void ingest(Command command);

    record Command(UUID routeId, String origin, String destination) {
        public Command {
            Objects.requireNonNull(routeId, "routeId must not be null");
            Objects.requireNonNull(origin, "origin must not be null");
            Objects.requireNonNull(destination, "destination must not be null");
        }
    }
}
