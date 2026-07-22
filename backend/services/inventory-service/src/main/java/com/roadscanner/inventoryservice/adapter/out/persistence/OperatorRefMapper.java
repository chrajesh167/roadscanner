package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.OperatorRef;

final class OperatorRefMapper {

    OperatorRef toDomain(OperatorRefJpaEntity entity) {
        return OperatorRef.of(entity.getOperatorId(), entity.getDisplayName());
    }

    OperatorRefJpaEntity toNewEntity(OperatorRef operatorRef) {
        return new OperatorRefJpaEntity(operatorRef.operatorId(), operatorRef.displayName());
    }

    void applyTo(OperatorRefJpaEntity entity, OperatorRef operatorRef) {
        entity.applyMutableState(operatorRef.displayName());
    }
}
