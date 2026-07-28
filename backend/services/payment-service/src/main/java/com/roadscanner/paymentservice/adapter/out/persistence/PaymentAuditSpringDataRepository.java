package com.roadscanner.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PaymentAuditSpringDataRepository extends JpaRepository<PaymentAuditRecordJpaEntity, UUID> {
}
