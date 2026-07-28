package com.roadscanner.paymentservice.domain.port.out;

import com.roadscanner.paymentservice.domain.model.GatewayType;

import java.time.Instant;

/** Persistence port for inbound webhook records — the backbone of idempotency and replay
 * protection (docs/services/payment-service/domain-model.md's "Webhook Verification"). Insert-only.
 * A repeated {@code gatewayEventId} is a no-op. */
public interface WebhookEventRepository {

    /** @return {@code true} if this gateway event id has already been recorded (a replay/duplicate). */
    boolean existsByGatewayEventId(GatewayType gatewayType, String gatewayEventId);

    /** Records the webhook (verified or rejected) for idempotency and audit. */
    void record(GatewayType gatewayType, String gatewayEventId, boolean signatureVerified, String payloadDigest,
                String processingOutcome, Instant receivedAt);
}
