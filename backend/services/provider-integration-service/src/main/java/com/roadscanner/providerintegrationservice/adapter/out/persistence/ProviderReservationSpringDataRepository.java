package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProviderReservationSpringDataRepository extends JpaRepository<ProviderReservationJpaEntity, UUID> {

    Optional<ProviderReservationJpaEntity> findByProviderTypeAndProviderBlockReference(String providerType,
                                                                                       String providerBlockReference);
}
