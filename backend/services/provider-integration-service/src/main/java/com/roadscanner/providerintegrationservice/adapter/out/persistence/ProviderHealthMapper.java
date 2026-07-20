package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

final class ProviderHealthMapper {

    ProviderHealth toDomain(ProviderHealthJpaEntity entity) {
        return ProviderHealth.reconstitute(new ProviderType(entity.getProviderType()),
                HealthState.valueOf(entity.getCurrentState()), entity.getLastCheckedAt(), entity.getLastSuccessAt(),
                entity.getLastFailureAt(), entity.getConsecutiveFailures(), entity.getUpdatedAt());
    }

    ProviderHealthJpaEntity toNewEntity(ProviderHealth health) {
        return new ProviderHealthJpaEntity(health.providerType().code(), health.currentState().name(),
                health.lastCheckedAt(), health.lastSuccessAt(), health.lastFailureAt(),
                health.consecutiveFailures(), health.updatedAt());
    }

    void applyTo(ProviderHealthJpaEntity entity, ProviderHealth health) {
        entity.applyMutableState(health.currentState().name(), health.lastCheckedAt(), health.lastSuccessAt(),
                health.lastFailureAt(), health.consecutiveFailures(), health.updatedAt());
    }
}
