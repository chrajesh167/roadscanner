package com.roadscanner.providerintegrationservice.domain.service;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves a {@link ProviderType} to the {@link ProviderClient} that implements it. This is the
 * entire mechanism behind "add a new provider without changing business logic": every use case
 * depends on this registry and the {@link ProviderClient} port only, never on a concrete
 * provider adapter — so onboarding RedBus means writing {@code adapter/out/provider/redbus/*}
 * and a config row, and nothing here changes.
 *
 * Framework-free by design (matching {@code search-service}'s {@code SearchRankingPolicy}
 * placement in {@code domain/service}) — Spring's job (in {@code config.UseCaseConfig}) is only
 * to collect every {@code ProviderClient} bean into the {@code List} this constructor takes; two
 * adapters declaring the same {@link ProviderType} is a startup-time configuration error, caught
 * here rather than silently letting one shadow the other.
 */
public class ProviderClientRegistry {

    private final Map<ProviderType, ProviderClient> clientsByType;

    public ProviderClientRegistry(List<ProviderClient> clients) {
        Objects.requireNonNull(clients, "clients must not be null");
        this.clientsByType = clients.stream()
                .collect(Collectors.toUnmodifiableMap(ProviderClient::supportedType, Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("Multiple ProviderClient beans declare the same "
                                    + "ProviderType: " + a.supportedType());
                        }));
    }

    public ProviderClient resolve(ProviderType providerType) {
        ProviderClient client = clientsByType.get(providerType);
        if (client == null) {
            throw new ProviderNotSupportedException(providerType);
        }
        return client;
    }

    public ProviderClient resolveWithCapability(ProviderType providerType, ProviderCapability capability) {
        ProviderClient client = resolve(providerType);
        if (!client.supportedCapabilities().contains(capability)) {
            throw new ProviderNotSupportedException(providerType);
        }
        return client;
    }

    public boolean isRegistered(ProviderType providerType) {
        return clientsByType.containsKey(providerType);
    }
}
