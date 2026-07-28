package com.roadscanner.paymentservice.domain.port.in;

import com.roadscanner.paymentservice.domain.model.GatewayType;

import java.util.Objects;

/**
 * {@code Handle Gateway Webhook} — verify signature, replay-check, correlate, apply, idempotently
 * (docs/services/payment-service/use-cases.md). The public, JWT-less endpoint, secured by gateway
 * signature verification instead (docs/services/payment-service/boundaries.md). A repeated webhook
 * for the same gateway event id is a no-op — never a double charge or duplicate
 * {@code PaymentCompleted}.
 */
public interface HandleGatewayWebhook {

    Result handle(Command command);

    record Command(GatewayType gatewayType, String rawPayload, String signatureHeader) {
        public Command {
            Objects.requireNonNull(gatewayType, "gatewayType must not be null");
            Objects.requireNonNull(rawPayload, "rawPayload must not be null");
        }
    }

    enum Outcome {APPLIED, DUPLICATE_IGNORED, UNCORRELATED, REJECTED}

    record Result(Outcome outcome) {
    }
}
