package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Raw Spring Data access for provider credentials, wrapped by
 * {@link ProviderCredentialsRepositoryAdapter} before anything outside this package touches it.
 *
 * <p>{@code findByProviderId} returns an {@code Optional} rather than a list because V6's unique
 * constraint guarantees at most one credential set per provider.
 */
interface ProviderCredentialsSpringDataRepository extends JpaRepository<ProviderCredentialsJpaEntity, UUID> {

    Optional<ProviderCredentialsJpaEntity> findByProviderId(UUID providerId);
}
