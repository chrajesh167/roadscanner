package com.roadscanner.paymentservice.domain.exception;

/** Thrown when a gateway webhook fails signature verification or replay checks — the webhook is
 * audited and rejected, never applied (docs/services/payment-service/domain-model.md's "Webhook
 * Verification"). Mapped to 400 so the gateway does not treat it as a retryable 5xx. */
public class WebhookVerificationException extends PaymentServiceException {

    public WebhookVerificationException(String message) {
        super(message);
    }
}
