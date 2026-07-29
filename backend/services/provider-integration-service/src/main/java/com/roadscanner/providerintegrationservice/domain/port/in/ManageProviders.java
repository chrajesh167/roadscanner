package com.roadscanner.providerintegrationservice.domain.port.in;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Administrative management of the provider registry — the inbound port behind
 * {@code /api/v1/providers}.
 *
 * <p>One port for the whole registry lifecycle rather than six single-method ports. These
 * operations share one aggregate, one set of invariants and one caller (an admin console); the
 * split that matters in this service is between the registry and the provider-facing operations
 * (search, seat map, booking), and that split is preserved.
 *
 * <p>Commands speak in domain value objects, so validation lives in the value objects themselves
 * and cannot be bypassed by a non-REST caller.
 */
public interface ManageProviders {

    List<Provider> list(boolean enabledOnly);

    /** @throws com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException
     *          if no provider with this id exists. */
    Provider get(ProviderId providerId);

    /** @throws com.roadscanner.providerintegrationservice.domain.exception.DuplicateProviderException
     *          if a provider with this type is already registered. */
    Provider register(RegisterProviderCommand command);

    /** @throws com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException
     *          if no provider with this id exists. */
    Provider update(UpdateProviderCommand command);

    /** Idempotent — see {@link Provider#enable}. */
    ToggleResult enable(ProviderId providerId);

    /** Idempotent — see {@link Provider#disable}. */
    ToggleResult disable(ProviderId providerId);

    record RegisterProviderCommand(ProviderType type, ProviderCategory category, String displayName,
                                   Set<ProviderCapability> capabilities, String baseUrl,
                                   int timeoutMillis, int retryCount) {
        public RegisterProviderCommand {
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(category, "category must not be null");
            Objects.requireNonNull(displayName, "displayName must not be null");
            Objects.requireNonNull(capabilities, "capabilities must not be null");
            capabilities = Set.copyOf(capabilities);
        }
    }

    record UpdateProviderCommand(ProviderId providerId, ProviderCategory category, String displayName,
                                 Set<ProviderCapability> capabilities, String baseUrl,
                                 int timeoutMillis, int retryCount) {
        public UpdateProviderCommand {
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(category, "category must not be null");
            Objects.requireNonNull(displayName, "displayName must not be null");
            Objects.requireNonNull(capabilities, "capabilities must not be null");
            capabilities = Set.copyOf(capabilities);
        }
    }

    /**
     * @param changed false when the provider was already in the requested state, so a retried
     *                enable/disable is reported honestly rather than pretending it did something
     */
    record ToggleResult(Provider provider, boolean changed) {
        public ToggleResult {
            Objects.requireNonNull(provider, "provider must not be null");
        }
    }
}
