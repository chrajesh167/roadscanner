package com.roadscanner.paymentservice.domain.model;

/**
 * The opaque identifiers a gateway hands back for a transaction — stored as plain strings, and
 * <strong>never</strong> containing card / bank / instrument data (NFR-12). Any field may be
 * {@code null} until the gateway has produced it. The domain treats these as opaque correlation
 * tokens; only the adapter that produced them knows their gateway-specific meaning
 * (docs/services/payment-service/domain-model.md).
 */
public record GatewayReference(String gatewayPaymentId, String gatewayOrderId, String gatewayRefundId) {

    public static GatewayReference none() {
        return new GatewayReference(null, null, null);
    }

    public static GatewayReference ofPayment(String gatewayOrderId, String gatewayPaymentId) {
        return new GatewayReference(gatewayPaymentId, gatewayOrderId, null);
    }

    public static GatewayReference ofRefund(String gatewayRefundId) {
        return new GatewayReference(null, null, gatewayRefundId);
    }
}
