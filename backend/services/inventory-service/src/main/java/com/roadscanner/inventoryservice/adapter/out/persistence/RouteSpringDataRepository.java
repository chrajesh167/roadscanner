package com.roadscanner.inventoryservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface RouteSpringDataRepository extends JpaRepository<RouteJpaEntity, UUID> {

    Optional<RouteJpaEntity> findByOriginCityIdAndDestinationCityId(UUID originCityId, UUID destinationCityId);
}
