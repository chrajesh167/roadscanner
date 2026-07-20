package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AuditRecordSpringDataRepository extends JpaRepository<AuditRecordJpaEntity, UUID> {
}
