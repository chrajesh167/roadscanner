package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.ProviderType;

import java.util.Objects;

/**
 * For one provider, search every known {@code Route} and reconcile results into {@code Trip}/
 * {@code SeatLayout}/{@code ProviderMapping} rows, then record a {@code SyncRecord} and publish
 * {@code CatalogSyncCompleted}/{@code CatalogSyncFailed}
 * (docs/services/inventory-service/use-cases.md — "Synchronize Provider Catalog").
 *
 * Reconciliation keys strictly on {@code (providerType, providerTripId)} identity: an existing
 * {@code ProviderMapping} for that pair means "update the existing Trip," anything else means
 * "create a new Trip." No cross-matching against first-party trips is attempted — see
 * {@code ProviderMapping}'s cardinality note (docs/services/inventory-service/domain-model.md)
 * for why that's a documented, deliberate simplification.
 */
public interface SynchronizeProviderCatalog {

    Result synchronize(Command command);

    record Command(ProviderType providerType) {
        public Command {
            Objects.requireNonNull(providerType, "providerType must not be null");
        }
    }

    record Result(int tripsReconciled, boolean succeeded) {
    }
}
