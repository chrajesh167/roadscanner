package com.roadscanner.paymentservice.adapter.out.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.paymentservice.domain.exception.GatewayUnavailableException;
import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.GatewayWebhookEvent;
import com.roadscanner.paymentservice.domain.model.WebhookEventType;
import com.roadscanner.paymentservice.domain.port.out.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared behavior for the stub gateway adapters (Razorpay/Stripe/Adyen). <strong>No real gateway
 * SDK is integrated yet</strong> — each concrete adapter is a deterministic in-process stub, the
 * "contract ready, adapter is the only new code" posture {@code provider-integration-service}'s
 * FlixBus integration already proved (docs/services/payment-service/overview.md).
 *
 * <p>This class and its subclasses are the <strong>only</strong> place a gateway-specific concern
 * lives; the domain never knows which gateway is in use (docs/services/payment-service/domain-model.md's
 * "Payment Gateway Abstraction"). When a real SDK replaces a stub, only that subclass changes.
 */
public abstract class AbstractStubPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(AbstractStubPaymentGateway.class);

    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    protected AbstractStubPaymentGateway(ObjectMapper objectMapper, String webhookSecret) {
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public GatewayReference initiateCharge(ChargeRequest request) {
        String code = type().code().toLowerCase();
        // A real adapter would call the gateway SDK here and translate a transport failure into
        // GatewayUnavailableException / PaymentDeclinedException. The stub always succeeds in
        // starting a charge; the actual capture arrives as a webhook.
        log.debug("[{}] stub initiateCharge for payment {}", code, request.paymentId());
        return GatewayReference.ofPayment(code + "-order-" + request.paymentId(),
                code + "-pay-" + request.paymentId());
    }

    @Override
    public GatewayReference refund(RefundRequest request) {
        String code = type().code().toLowerCase();
        log.debug("[{}] stub refund for payment {} refund {}", code, request.paymentId(), request.refundId());
        return GatewayReference.ofRefund(code + "-refund-" + request.refundId());
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        return WebhookSignatureVerifier.verify(rawPayload, signatureHeader, webhookSecret);
    }

    @Override
    public GatewayWebhookEvent parseWebhook(String rawPayload) {
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            WebhookEventType eventType = parseType(node.path("type").asText(null));
            return new GatewayWebhookEvent(type(), requireText(node, "gatewayEventId"), eventType,
                    node.path("gatewayPaymentId").asText(null), node.path("gatewayRefundId").asText(null),
                    node.path("failureCode").asText(null), node.path("failureReason").asText(null));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new GatewayUnavailableException("Malformed webhook payload for gateway " + type());
        }
    }

    private WebhookEventType parseType(String raw) {
        if (raw == null) {
            return WebhookEventType.UNKNOWN;
        }
        try {
            return WebhookEventType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return WebhookEventType.UNKNOWN;
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new GatewayUnavailableException("Webhook missing required field '" + field + "'");
        }
        return value.asText();
    }

    protected static GatewayType typeOf(String code) {
        return new GatewayType(code);
    }
}
