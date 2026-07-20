package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProviderConfigurationSpringDataRepository extends JpaRepository<ProviderConfigurationJpaEntity, UUID> {

    Optional<ProviderConfigurationJpaEntity> findByProviderType(String providerType);

    List<ProviderConfigurationJpaEntity> findByEnabledTrue();
}
