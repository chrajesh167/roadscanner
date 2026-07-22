package com.roadscanner.inventoryservice.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface StationSpringDataRepository extends JpaRepository<StationJpaEntity, UUID> {

    List<StationJpaEntity> findByNameStartingWithIgnoreCaseAndCityId(String prefix, UUID cityId, Pageable pageable);

    List<StationJpaEntity> findByNameStartingWithIgnoreCase(String prefix, Pageable pageable);
}
