package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;

/**
 * The canonical, gateway-agnostic shape a gateway adapter translates its own raw webhook payload
 * into (docs/services/payment-service/domain-model.md's "Webhook Verification"). The application
 * layer applies transitions off this shape and never sees the gateway's raw payload — no
 * instrument-adjacent data (NFR-12) crosses this boundary.
 *
 * @param gatewayType      which gateway delivered this event
 * @param gatewayEventId   the gateway's own unique event id — the replay/de-duplication key
 * @param type             the canonical classification
 * @param gatewayPaymentId the gateway payment reference this event correlates to (may be null)
 * @param gatewayRefundId  the gateway refund reference this event correlates to (may be null)
 * @param failureCode      gateway-agnostic failure code, for FAILED events (may be null)
 * @param failureReason    human-readable reason, for FAILED events (may be null)
 */
public record GatewayWebhookEvent(GatewayType gatewayType, String gatewayEventId, WebhookEventType type,
                                  String gatewayPaymentId, String gatewayRefundId, String failureCode,
                                  String failureReason) {

    public GatewayWebhookEvent {
        Objects.requireNonNull(gatewayType, "gatewayType must not be null");
        Objects.requireNonNull(gatewayEventId, "gatewayEventId must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
