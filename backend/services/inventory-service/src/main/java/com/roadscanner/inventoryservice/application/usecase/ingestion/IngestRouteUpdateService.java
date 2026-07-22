package com.roadscanner.inventoryservice.application.usecase.ingestion;

import com.roadscanner.inventoryservice.domain.port.in.IngestRouteUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implements {@link IngestRouteUpdate} — acknowledgment-only by design; see that port's
 * Javadoc for the documented reason no reconciliation happens here. */
public class IngestRouteUpdateService implements IngestRouteUpdate {

    private static final Logger log = LoggerFactory.getLogger(IngestRouteUpdateService.class);

    @Override
    public void ingest(Command command) {
        log.info("Received RouteUpdated for operator-service route {} ({} -> {}) — acknowledged, "
                        + "no reconciliation performed (this service's Route catalog is administratively managed; "
                        + "see IngestRouteUpdate's Javadoc)",
                command.routeId(), command.origin(), command.destination());
    }
}
