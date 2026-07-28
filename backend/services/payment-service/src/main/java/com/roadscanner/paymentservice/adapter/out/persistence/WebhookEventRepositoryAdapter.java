package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.port.out.WebhookEventRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
class WebhookEventRepositoryAdapter implements WebhookEventRepository {

    private final WebhookEventSpringDataRepository springDataRepository;

    WebhookEventRepositoryAdapter(WebhookEventSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public boolean existsByGatewayEventId(GatewayType gatewayType, String gatewayEventId) {
        return springDataRepository.existsByGatewayTypeAndGatewayEventId(gatewayType.code(), gatewayEventId);
    }

    @Override
    public void record(GatewayType gatewayType, String gatewayEventId, boolean signatureVerified, String payloadDigest,
                       String processingOutcome, Instant receivedAt) {
        springDataRepository.save(new WebhookEventJpaEntity(UUID.randomUUID(), gatewayType.code(), gatewayEventId,
                signatureVerified, payloadDigest, processingOutcome, receivedAt));
    }
}
