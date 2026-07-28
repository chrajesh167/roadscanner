package com.roadscanner.paymentservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Insert-only ledger line — the internal transaction ledger
 * (docs/architecture/service-boundaries.md's {@code payment-service} entry). */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransactionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "refund_id", updatable = false)
    private UUID refundId;

    @Column(name = "type", nullable = false, updatable = false)
    private String type;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "gateway_payment_id", updatable = false)
    private String gatewayPaymentId;

    @Column(name = "gateway_refund_id", updatable = false)
    private String gatewayRefundId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PaymentTransactionJpaEntity() {
    }

    PaymentTransactionJpaEntity(UUID id, UUID paymentId, UUID refundId, String type, BigDecimal amount,
                                String currency, String gatewayPaymentId, String gatewayRefundId, Instant occurredAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.refundId = refundId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewayRefundId = gatewayRefundId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public String getGatewayRefundId() {
        return gatewayRefundId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
