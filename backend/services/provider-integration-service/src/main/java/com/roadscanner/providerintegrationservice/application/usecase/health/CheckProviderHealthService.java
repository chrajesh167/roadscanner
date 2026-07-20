package com.roadscanner.providerintegrationservice.application.usecase.health;

import com.roadscanner.providerintegrationservice.application.usecase.audit.AuditRecorder;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.AuditEventType;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.port.in.CheckProviderHealth;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderHealthRepository;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.time.Clock;

/**
 * Implements {@link CheckProviderHealth} — performs a live probe, records it, and publishes an
 * audit event only on the two transitions that matter: into {@link HealthState#UNAVAILABLE} from
 * anything else, and back to {@link HealthState#HEALTHY} specifically from
 * {@link HealthState#UNAVAILABLE} (a "recovery" only means something if the provider was
 * previously known to be down — the very first check ever recorded, {@link HealthState#UNKNOWN}
 * → {@code HEALTHY}, is not a recovery). Shared by the REST health endpoint and
 * {@code ProviderHealthMonitor}'s scheduled sweep — see this port's Javadoc.
 */
public class CheckProviderHealthService implements CheckProviderHealth {

    private final ProviderConfigurationRepository configurationRepository;
    private final ProviderClientRegistry registry;
    private final ProviderHealthRepository healthRepository;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public CheckProviderHealthService(ProviderConfigurationRepository configurationRepository,
                                       ProviderClientRegistry registry, ProviderHealthRepository healthRepository,
                                       AuditRecorder auditRecorder, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.registry = registry;
        this.healthRepository = healthRepository;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Override
    public Result check(Command command) {
        Provider provider = configurationRepository.findByType(command.providerType())
                .orElseThrow(() -> new ProviderNotSupportedException(command.providerType()));
        ProviderClient client = registry.resolve(command.providerType());
        ProviderHealthCheck probeResult = client.checkHealth(provider);

        ProviderHealth health = healthRepository.findByProviderType(command.providerType())
                .orElseGet(() -> ProviderHealth.unknown(command.providerType(), clock.instant()));
        HealthState previousState = health.currentState();
        health.recordCheck(probeResult);
        ProviderHealth saved = healthRepository.save(health);

        publishTransitionIfAny(command.providerType(), previousState, saved.currentState());
        return new Result(saved);
    }

    private void publishTransitionIfAny(com.roadscanner.providerintegrationservice.domain.model.ProviderType providerType,
                                         HealthState previousState, HealthState newState) {
        if (previousState != HealthState.UNAVAILABLE && newState == HealthState.UNAVAILABLE) {
            auditRecorder.record(providerType, AuditEventType.PROVIDER_UNAVAILABLE, null,
                    "Provider " + providerType + " transitioned to UNAVAILABLE");
        } else if (previousState == HealthState.UNAVAILABLE && newState == HealthState.HEALTHY) {
            auditRecorder.record(providerType, AuditEventType.PROVIDER_RECOVERED, null,
                    "Provider " + providerType + " recovered to HEALTHY");
        }
    }
}
