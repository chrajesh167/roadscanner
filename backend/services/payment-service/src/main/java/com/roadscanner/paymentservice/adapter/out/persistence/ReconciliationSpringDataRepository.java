package com.roadscanner.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ReconciliationSpringDataRepository extends JpaRepository<ReconciliationRecordJpaEntity, UUID> {
}
