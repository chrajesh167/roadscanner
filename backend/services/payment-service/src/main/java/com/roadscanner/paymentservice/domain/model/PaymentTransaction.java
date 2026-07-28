package com.roadscanner.paymentservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One immutable ledger line per actual money movement — a capture or a refund — with its gateway
 * references. This is the "internal ledger of transactions" docs/architecture/service-boundaries.md
 * assigns to this service. Insert-only, never updated or deleted (the same insert-only posture
 * {@code provider-integration-service}'s {@code AuditRecord} takes). The data a future
 * accounting/settlement service would consume; not itself settlement
 * (docs/services/payment-service/boundaries.md).
 */
public record PaymentTransaction(TransactionId id, PaymentId paymentId, RefundId refundId, TransactionType type,
                                 Money amount, GatewayReference gatewayReference, Instant occurredAt) {

    public PaymentTransaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static PaymentTransaction capture(PaymentId paymentId, Money amount, GatewayReference gatewayReference,
                                             Instant occurredAt) {
        return new PaymentTransaction(TransactionId.generate(), paymentId, null, TransactionType.CAPTURE, amount,
                gatewayReference, occurredAt);
    }

    public static PaymentTransaction refund(PaymentId paymentId, RefundId refundId, Money amount,
                                            GatewayReference gatewayReference, Instant occurredAt) {
        return new PaymentTransaction(TransactionId.generate(), paymentId, refundId, TransactionType.REFUND, amount,
                gatewayReference, occurredAt);
    }
}
