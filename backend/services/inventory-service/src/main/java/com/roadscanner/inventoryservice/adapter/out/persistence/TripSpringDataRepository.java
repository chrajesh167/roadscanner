package com.roadscanner.inventoryservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TripSpringDataRepository extends JpaRepository<TripJpaEntity, UUID> {

    List<TripJpaEntity> findByOperatorId(UUID operatorId);
}
