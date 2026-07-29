package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;

import java.util.Objects;

/**
 * Probes a provider on demand from the admin console, addressed by its registry id.
 *
 * <p>Deliberately a thin adapter over {@link CheckProviderHealth} rather than a second probe
 * implementation. The "probe, record the result, publish on state transition" rule stays in one
 * place regardless of whether the trigger was the scheduler, the internal health endpoint, or an
 * admin clicking Test — which is the same reason {@code CheckProviderHealth} was shared between
 * its two original callers.
 *
 * <p>All this port adds is the id→type translation: the admin API speaks registry ids, while
 * everything provider-facing speaks {@code ProviderType}.
 */
public interface TestProviderConnection {

    Result test(Command command);

    record Command(ProviderId providerId) {
        public Command {
            Objects.requireNonNull(providerId, "providerId must not be null");
        }
    }

    /**
     * @throws com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException
     *         if no provider with this id exists
     */
    record Result(ProviderHealth health) {
        public Result {
            Objects.requireNonNull(health, "health must not be null");
        }
    }
}
