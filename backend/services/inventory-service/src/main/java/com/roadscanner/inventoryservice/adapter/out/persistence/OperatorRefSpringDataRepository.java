package com.roadscanner.inventoryservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OperatorRefSpringDataRepository extends JpaRepository<OperatorRefJpaEntity, UUID> {
}
