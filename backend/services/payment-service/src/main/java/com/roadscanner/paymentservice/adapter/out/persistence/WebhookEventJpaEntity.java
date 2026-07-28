package com.roadscanner.paymentservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Insert-only record of an inbound webhook — the backbone of idempotency and replay protection.
 * The unique constraint on ({@code gateway_type}, {@code gateway_event_id}) makes a redelivered
 * webhook a no-op (docs/services/payment-service/domain-model.md). */
@Entity
@Table(name = "webhook_events")
public class WebhookEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "gateway_type", nullable = false, updatable = false)
    private String gatewayType;

    @Column(name = "gateway_event_id", nullable = false, updatable = false)
    private String gatewayEventId;

    @Column(name = "signature_verified", nullable = false, updatable = false)
    private boolean signatureVerified;

    @Column(name = "payload_digest", updatable = false)
    private String payloadDigest;

    @Column(name = "processing_outcome", nullable = false, updatable = false)
    private String processingOutcome;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected WebhookEventJpaEntity() {
    }

    WebhookEventJpaEntity(UUID id, String gatewayType, String gatewayEventId, boolean signatureVerified,
                          String payloadDigest, String processingOutcome, Instant receivedAt) {
        this.id = id;
        this.gatewayType = gatewayType;
        this.gatewayEventId = gatewayEventId;
        this.signatureVerified = signatureVerified;
        this.payloadDigest = payloadDigest;
        this.processingOutcome = processingOutcome;
        this.receivedAt = receivedAt;
    }
}
