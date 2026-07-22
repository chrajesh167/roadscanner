package com.roadscanner.inventoryservice.adapter.out.kafka;

import java.time.Instant;

public record CatalogSyncMessage(String eventType, String providerType, Integer tripsReconciled,
                                  Long catalogVersion, String errorDetail, Instant occurredAt) {
}
