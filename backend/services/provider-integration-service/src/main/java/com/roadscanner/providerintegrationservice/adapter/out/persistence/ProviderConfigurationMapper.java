package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** The only class in this package that translates between {@code domain.model} and
 * {@link ProviderConfigurationJpaEntity} — read-only, matching the port's read-only contract. */
final class ProviderConfigurationMapper {

    private static final String CAPABILITIES_DELIMITER = ",";

    Provider toDomain(ProviderConfigurationJpaEntity entity) {
        Set<ProviderCapability> capabilities = Arrays.stream(entity.getCapabilities().split(CAPABILITIES_DELIMITER))
                .filter(value -> !value.isBlank())
                .map(ProviderCapability::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return Provider.reconstitute(new ProviderId(entity.getId()), new ProviderType(entity.getProviderType()),
                entity.getDisplayName(), entity.isEnabled(), capabilities, entity.getBaseUrl(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
