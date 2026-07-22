package com.roadscanner.inventoryservice.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CitySpringDataRepository extends JpaRepository<CityJpaEntity, UUID> {

    List<CityJpaEntity> findByNameStartingWithIgnoreCase(String prefix, Pageable pageable);
}
