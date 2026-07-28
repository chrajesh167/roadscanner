package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.port.out.WebhookEventRepository;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWebhookEventRepository implements WebhookEventRepository {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    @Override
    public boolean existsByGatewayEventId(GatewayType gatewayType, String gatewayEventId) {
        return seen.contains(gatewayType.code() + "|" + gatewayEventId);
    }

    @Override
    public void record(GatewayType gatewayType, String gatewayEventId, boolean signatureVerified, String payloadDigest,
                       String processingOutcome, Instant receivedAt) {
        seen.add(gatewayType.code() + "|" + gatewayEventId);
    }
}
