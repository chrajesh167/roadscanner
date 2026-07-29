package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** The only class in this package that translates between {@code domain.model} and
 * {@link ProviderConfigurationJpaEntity}. */
final class ProviderConfigurationMapper {

    private static final String CAPABILITIES_DELIMITER = ",";

    Provider toDomain(ProviderConfigurationJpaEntity entity) {
        return Provider.reconstitute(
                new ProviderId(entity.getId()),
                new ProviderType(entity.getProviderType()),
                new ProviderCategory(entity.getProviderCategory()),
                entity.getDisplayName(),
                entity.isEnabled(),
                parseCapabilities(entity.getCapabilities()),
                entity.getBaseUrl(),
                entity.getTimeoutMs(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    ProviderConfigurationJpaEntity toEntity(Provider provider) {
        return new ProviderConfigurationJpaEntity(
                provider.id().value(),
                provider.type().code(),
                provider.category().code(),
                provider.displayName(),
                provider.enabled(),
                formatCapabilities(provider.capabilities()),
                provider.baseUrl(),
                provider.timeoutMillis(),
                provider.retryCount(),
                provider.createdAt(),
                provider.updatedAt());
    }

    /**
     * Copies mutable state onto an entity already managed by the persistence context, so an update
     * is a dirty check on the loaded row rather than a blind whole-row overwrite — and so the
     * {@code @Version} column keeps doing its job.
     *
     * <p>{@code providerType} is deliberately not copied: it is the row's identity, and every
     * {@code provider_sessions} and {@code provider_health} row is keyed on it.
     */
    void applyTo(ProviderConfigurationJpaEntity entity, Provider provider) {
        entity.setProviderCategory(provider.category().code());
        entity.setDisplayName(provider.displayName());
        entity.setEnabled(provider.enabled());
        entity.setCapabilities(formatCapabilities(provider.capabilities()));
        entity.setBaseUrl(provider.baseUrl());
        entity.setTimeoutMs(provider.timeoutMillis());
        entity.setRetryCount(provider.retryCount());
        entity.setUpdatedAt(provider.updatedAt());
    }

    private static Set<ProviderCapability> parseCapabilities(String raw) {
        return Arrays.stream(raw.split(CAPABILITIES_DELIMITER))
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .map(ProviderCapability::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Sorted before joining, so an unchanged capability set always serialises identically.
     * Otherwise {@code Set}'s undefined iteration order could make a no-op save look like a
     * modification, bumping {@code updated_at} and the optimistic-locking version for nothing.
     */
    private static String formatCapabilities(Set<ProviderCapability> capabilities) {
        return capabilities.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(CAPABILITIES_DELIMITER));
    }
}
