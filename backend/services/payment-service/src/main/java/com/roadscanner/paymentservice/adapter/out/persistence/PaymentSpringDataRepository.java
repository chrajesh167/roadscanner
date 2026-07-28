package com.roadscanner.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PaymentSpringDataRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentJpaEntity> findByBookingReferenceAndStatusNotIn(UUID bookingReference, List<String> terminalStatuses);

    List<PaymentJpaEntity> findByBookingReference(UUID bookingReference);

    Optional<PaymentJpaEntity> findByGatewayPaymentId(String gatewayPaymentId);

    List<PaymentJpaEntity> findByStatusInAndExpiresAtBefore(List<String> preCaptureStatuses, Instant cutoff);
}
