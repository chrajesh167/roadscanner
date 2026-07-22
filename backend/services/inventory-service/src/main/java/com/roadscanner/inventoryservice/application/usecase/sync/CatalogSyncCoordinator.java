package com.roadscanner.inventoryservice.application.usecase.sync;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.in.SynchronizeProviderCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Drives {@link SynchronizeProviderCatalog} for every configured provider type — the
 * orchestration behind {@code config}'s scheduled sync job, matching
 * {@code provider-integration-service}'s {@code ProviderHealthMonitor} pattern: scheduling is a
 * config-layer concern, the business logic it triggers is not. One provider's synchronization
 * failing does not stop the sweep for the others.
 *
 * The candidate provider list is this service's own configuration
 * ({@code roadscanner.inventory.sync.provider-types}), not fetched from
 * {@code provider-integration-service} — that service exposes no "list enabled providers"
 * endpoint (its REST surface is entirely per-{@code providerType}), so a provider not actually
 * configured there simply fails this call with {@code ProviderNotSupportedException} (surfaced
 * here as a caught, logged failure), never a contract change on either service. See the
 * implementation summary delivered with this service for the full reasoning.
 */
public class CatalogSyncCoordinator {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncCoordinator.class);

    private final SynchronizeProviderCatalog synchronizeProviderCatalog;
    private final List<ProviderType> configuredProviderTypes;

    public CatalogSyncCoordinator(SynchronizeProviderCatalog synchronizeProviderCatalog,
                                   List<ProviderType> configuredProviderTypes) {
        this.synchronizeProviderCatalog = synchronizeProviderCatalog;
        this.configuredProviderTypes = List.copyOf(configuredProviderTypes);
    }

    public void synchronizeAll() {
        for (ProviderType providerType : configuredProviderTypes) {
            try {
                SynchronizeProviderCatalog.Result result =
                        synchronizeProviderCatalog.synchronize(new SynchronizeProviderCatalog.Command(providerType));
                log.info("Catalog sync for {}: succeeded={}, tripsReconciled={}", providerType,
                        result.succeeded(), result.tripsReconciled());
            } catch (RuntimeException e) {
                log.error("Catalog sync coordinator: unexpected failure for provider {}", providerType, e);
            }
        }
    }
}
