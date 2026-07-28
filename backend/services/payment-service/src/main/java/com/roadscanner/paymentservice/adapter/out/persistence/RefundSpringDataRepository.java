package com.roadscanner.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RefundSpringDataRepository extends JpaRepository<RefundJpaEntity, UUID> {

    Optional<RefundJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<RefundJpaEntity> findByPaymentId(UUID paymentId);

    Optional<RefundJpaEntity> findByGatewayRefundId(String gatewayRefundId);
}
