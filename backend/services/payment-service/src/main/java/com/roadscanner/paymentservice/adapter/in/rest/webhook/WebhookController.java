package com.roadscanner.paymentservice.adapter.in.rest.webhook;

import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.port.in.HandleGatewayWebhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The <strong>public, JWT-less</strong> webhook endpoint — called by the external gateway, which
 * has no RoadScanner token. Secured by gateway signature verification instead of JWT
 * (docs/services/payment-service/boundaries.md's "Payment &harr; Auth and the Public Webhook
 * Boundary"). A signature failure throws {@code WebhookVerificationException} (400); a duplicate is
 * a 200 no-op. {@code api-gateway} must route {@code /webhooks/**} here without JWT enforcement.
 */
@RestController
@RequestMapping("/webhooks")
@Tag(name = "Gateway Webhooks", description = "Inbound payment-gateway webhooks (signature-verified, no JWT)")
class WebhookController {

    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final HandleGatewayWebhook handleGatewayWebhook;

    WebhookController(HandleGatewayWebhook handleGatewayWebhook) {
        this.handleGatewayWebhook = handleGatewayWebhook;
    }

    @PostMapping("/{gatewayType}")
    @Operation(summary = "Receive a gateway webhook",
            description = "Verifies the signature, replay-checks, correlates, and applies idempotently.")
    WebhookAck receive(@PathVariable String gatewayType,
                       @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                       @RequestBody String rawPayload) {
        HandleGatewayWebhook.Result result = handleGatewayWebhook.handle(
                new HandleGatewayWebhook.Command(new GatewayType(gatewayType), rawPayload, signature));
        return new WebhookAck(result.outcome().name());
    }
}
