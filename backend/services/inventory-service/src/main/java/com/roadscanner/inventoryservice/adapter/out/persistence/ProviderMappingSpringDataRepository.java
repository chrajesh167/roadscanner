package com.roadscanner.inventoryservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProviderMappingSpringDataRepository extends JpaRepository<ProviderMappingJpaEntity, UUID> {

    Optional<ProviderMappingJpaEntity> findByProviderTypeAndProviderTripId(String providerType, String providerTripId);
}
