package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.SyncRecord;
import com.roadscanner.inventoryservice.domain.model.SyncRecordId;
import com.roadscanner.inventoryservice.domain.model.SyncStatus;

final class SyncRecordMapper {

    SyncRecord toDomain(SyncRecordJpaEntity entity) {
        return SyncRecord.reconstitute(new SyncRecordId(entity.getId()), new ProviderType(entity.getProviderType()),
                entity.getLastAttemptAt(), SyncStatus.valueOf(entity.getStatus()), entity.getCatalogVersion(),
                entity.getErrorDetail());
    }

    SyncRecordJpaEntity toNewEntity(SyncRecord record) {
        return new SyncRecordJpaEntity(record.id().value(), record.providerType().code(), record.lastAttemptAt(),
                record.status().name(), record.catalogVersion(), record.errorDetail().orElse(null));
    }

    void applyTo(SyncRecordJpaEntity entity, SyncRecord record) {
        entity.applyMutableState(record.lastAttemptAt(), record.status().name(), record.catalogVersion(),
                record.errorDetail().orElse(null));
    }
}
