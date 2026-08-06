package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProviderBookingSpringDataRepository extends JpaRepository<ProviderBookingJpaEntity, UUID> {

    Optional<ProviderBookingJpaEntity> findByProviderTypeAndProviderOrderReference(String providerType,
                                                                                   String providerOrderReference);
}
