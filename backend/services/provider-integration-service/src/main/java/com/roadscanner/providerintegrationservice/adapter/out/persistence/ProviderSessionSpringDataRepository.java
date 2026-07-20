package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ProviderSessionSpringDataRepository extends JpaRepository<ProviderSessionJpaEntity, UUID> {

    List<ProviderSessionJpaEntity> findByStatusAndTokenExpiresAtLessThanEqual(String status, Instant asOf);
}
