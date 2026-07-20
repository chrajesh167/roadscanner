package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProviderHealthSpringDataRepository extends JpaRepository<ProviderHealthJpaEntity, String> {
}
