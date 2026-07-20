package com.roadscanner.providerintegrationservice.adapter.in.rest.health;

import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;

import java.time.Instant;

public record ProviderHealthResponse(String providerType, String currentState, Instant lastCheckedAt,
                                      Instant lastSuccessAt, Instant lastFailureAt, int consecutiveFailures) {

    public static ProviderHealthResponse from(ProviderHealth health) {
        return new ProviderHealthResponse(health.providerType().code(), health.currentState().name(),
                health.lastCheckedAt(), health.lastSuccessAt(), health.lastFailureAt(), health.consecutiveFailures());
    }
}
