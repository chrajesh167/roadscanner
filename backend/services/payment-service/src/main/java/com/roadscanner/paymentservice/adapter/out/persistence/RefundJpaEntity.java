package com.roadscanner.paymentservice.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistence shape for {@code Refund} — its own aggregate, its own optimistic-locked lifecycle. */
@Entity
@Table(name = "refunds")
public class RefundJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "booking_reference", nullable = false, updatable = false)
    private UUID bookingReference;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "full_refund", nullable = false, updatable = false)
    private boolean fullRefund;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "gateway_refund_id")
    private String gatewayRefundId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "refund_attempts", joinColumns = @JoinColumn(name = "refund_id"))
    @OrderColumn(name = "attempt_index")
    private List<RefundAttemptEmbeddable> attempts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RefundJpaEntity() {
    }

    RefundJpaEntity(UUID id, UUID paymentId, UUID bookingReference, BigDecimal amount, String currency,
                    boolean fullRefund, String reason, String status, String idempotencyKey, String gatewayRefundId,
                    List<RefundAttemptEmbeddable> attempts, Instant createdAt, Instant completedAt, Instant failedAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.bookingReference = bookingReference;
        this.amount = amount;
        this.currency = currency;
        this.fullRefund = fullRefund;
        this.reason = reason;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.gatewayRefundId = gatewayRefundId;
        this.attempts = new ArrayList<>(attempts);
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.failedAt = failedAt;
    }

    void applyMutableState(String status, String gatewayRefundId, List<RefundAttemptEmbeddable> attempts,
                           Instant completedAt, Instant failedAt) {
        this.status = status;
        this.gatewayRefundId = gatewayRefundId;
        this.attempts.clear();
        this.attempts.addAll(attempts);
        this.completedAt = completedAt;
        this.failedAt = failedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getBookingReference() {
        return bookingReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isFullRefund() {
        return fullRefund;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getGatewayRefundId() {
        return gatewayRefundId;
    }

    public List<RefundAttemptEmbeddable> getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public long getVersion() {
        return version;
    }
}
