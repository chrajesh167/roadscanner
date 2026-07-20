package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.AuditEventType;
import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

final class AuditRecordMapper {

    AuditRecord toDomain(AuditRecordJpaEntity entity) {
        ProviderSessionId sessionId = entity.getSessionId() == null ? null : new ProviderSessionId(entity.getSessionId());
        return new AuditRecord(entity.getId(), new ProviderType(entity.getProviderType()),
                AuditEventType.valueOf(entity.getEventType()), sessionId, entity.getMessage(), entity.getOccurredAt());
    }

    AuditRecordJpaEntity toNewEntity(AuditRecord record) {
        java.util.UUID sessionId = record.sessionId() == null ? null : record.sessionId().value();
        return new AuditRecordJpaEntity(record.id(), record.providerType().code(), record.eventType().name(),
                sessionId, record.message(), record.occurredAt());
    }
}
