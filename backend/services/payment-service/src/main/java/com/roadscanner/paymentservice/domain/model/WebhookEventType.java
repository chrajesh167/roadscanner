package com.roadscanner.paymentservice.domain.model;

/**
 * The canonical, gateway-agnostic classification a gateway adapter translates its own webhook
 * payload into (docs/services/payment-service/domain-model.md's "Webhook Verification"). Every
 * gateway's provider-specific event vocabulary is mapped to one of these at the adapter boundary,
 * so the application layer never sees a gateway-specific event name.
 */
public enum WebhookEventType {
    PAYMENT_AUTHORIZED,
    PAYMENT_CAPTURED,
    PAYMENT_FAILED,
    REFUND_COMPLETED,
    REFUND_FAILED,
    UNKNOWN
}
