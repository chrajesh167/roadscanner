package com.roadscanner.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PaymentTransactionSpringDataRepository extends JpaRepository<PaymentTransactionJpaEntity, UUID> {

    List<PaymentTransactionJpaEntity> findByPaymentId(UUID paymentId);
}
