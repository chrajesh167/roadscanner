package com.roadscanner.inventoryservice.config;

import com.roadscanner.inventoryservice.application.usecase.sync.CatalogSyncCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The only scheduled job this service runs — a thin trigger bean; all logic lives in
 * {@link CatalogSyncCoordinator} (framework-free, independently testable), matching
 * {@code provider-integration-service}'s {@code ProviderMaintenanceScheduler} pattern. */
@Component
public class CatalogSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncScheduler.class);

    private final CatalogSyncCoordinator catalogSyncCoordinator;

    public CatalogSyncScheduler(CatalogSyncCoordinator catalogSyncCoordinator) {
        this.catalogSyncCoordinator = catalogSyncCoordinator;
    }

    @Scheduled(fixedDelayString = "${roadscanner.inventory.sync.schedule-interval}")
    public void synchronizeCatalog() {
        try {
            catalogSyncCoordinator.synchronizeAll();
        } catch (RuntimeException e) {
            log.error("Catalog synchronization sweep failed unexpectedly", e);
        }
    }
}
