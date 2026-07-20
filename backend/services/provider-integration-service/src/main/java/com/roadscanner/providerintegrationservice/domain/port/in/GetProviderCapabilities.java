package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.Objects;
import java.util.Set;

/** Reports which {@link ProviderCapability capabilities} a provider actually supports —
 * "capability discovery" per this service's responsibilities. Backed by
 * {@code ProviderConfigurationRepository} (this service's own record of what's enabled/declared)
 * intersected with what the resolved {@code ProviderClient} adapter itself reports, so a
 * capability is never advertised unless both the configuration and the adapter agree it's
 * implemented. */
public interface GetProviderCapabilities {

    Result getCapabilities(Command command);

    record Command(ProviderType providerType) {
        public Command {
            Objects.requireNonNull(providerType, "providerType must not be null");
        }
    }

    record Result(ProviderType providerType, Set<ProviderCapability> capabilities) {
        public Result {
            Objects.requireNonNull(providerType, "providerType must not be null");
            Objects.requireNonNull(capabilities, "capabilities must not be null");
            capabilities = Set.copyOf(capabilities);
        }
    }
}
