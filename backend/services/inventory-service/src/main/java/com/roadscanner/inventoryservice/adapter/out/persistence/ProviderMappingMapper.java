package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.SyncStatus;
import com.roadscanner.inventoryservice.domain.model.TripId;

final class ProviderMappingMapper {

    ProviderMapping toDomain(ProviderMappingJpaEntity entity) {
        return ProviderMapping.reconstitute(new TripId(entity.getTripId()), new ProviderType(entity.getProviderType()),
                entity.getProviderTripId(), entity.getLastSyncedAt(), SyncStatus.valueOf(entity.getSyncStatus()));
    }

    ProviderMappingJpaEntity toNewEntity(ProviderMapping mapping) {
        return new ProviderMappingJpaEntity(mapping.tripId().value(), mapping.providerType().code(),
                mapping.providerTripId(), mapping.lastSyncedAt(), mapping.syncStatus().name());
    }

    void applyTo(ProviderMappingJpaEntity entity, ProviderMapping mapping) {
        entity.applyMutableState(mapping.lastSyncedAt(), mapping.syncStatus().name());
    }
}
