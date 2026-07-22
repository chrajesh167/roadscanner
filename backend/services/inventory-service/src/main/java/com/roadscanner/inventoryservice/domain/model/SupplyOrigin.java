package com.roadscanner.inventoryservice.domain.model;

/** Marks whether a {@link Trip} was ingested from {@code operator-service} (first-party) or
 * discovered by catalog synchronization against a provider (third-party) — see
 * docs/services/inventory-service/domain-model.md's {@code Trip} entry. Internal bookkeeping
 * only; never exposed to a caller of this service's API — a canonical trip has one identity
 * regardless of supply origin (docs/services/inventory-service/overview.md, "Two Supply
 * Sources, One Catalog"). */
public enum SupplyOrigin {
    FIRST_PARTY,
    PROVIDER_SYNCED
}
