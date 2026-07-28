package com.roadscanner.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface WebhookEventSpringDataRepository extends JpaRepository<WebhookEventJpaEntity, UUID> {

    boolean existsByGatewayTypeAndGatewayEventId(String gatewayType, String gatewayEventId);
}
