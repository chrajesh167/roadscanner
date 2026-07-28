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

/**
 * Persistence shape for {@code Payment} — the platform's only source of truth for payment state.
 * <strong>No card / bank / instrument data</strong> is stored (NFR-12) — only gateway references
 * and status. Attempts live in a child table via {@code @ElementCollection}, matching
 * {@code booking-service}'s {@code PassengerEmbeddable} precedent. {@code @Version} backs the
 * optimistic locking documented in docs/services/payment-service/domain-model.md's "Concurrency".
 */
@Entity
@Table(name = "payments")
public class PaymentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "booking_reference", nullable = false, updatable = false)
    private UUID bookingReference;

    @Column(name = "traveler_id", nullable = false, updatable = false)
    private UUID travelerId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "method", nullable = false, updatable = false)
    private String method;

    @Column(name = "gateway_type", nullable = false, updatable = false)
    private String gatewayType;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "gateway_refund_id")
    private String gatewayRefundId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "payment_attempts", joinColumns = @JoinColumn(name = "payment_id"))
    @OrderColumn(name = "attempt_index")
    private List<PaymentAttemptEmbeddable> attempts = new ArrayList<>();

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentJpaEntity() {
    }

    PaymentJpaEntity(UUID id, UUID bookingReference, UUID travelerId, BigDecimal amount, String currency,
                     String method, String gatewayType, String gatewayPaymentId, String gatewayOrderId,
                     String gatewayRefundId, String status, String idempotencyKey,
                     List<PaymentAttemptEmbeddable> attempts, Instant expiresAt, Instant createdAt,
                     Instant authorizedAt, Instant capturedAt, Instant failedAt, Instant cancelledAt,
                     Instant expiredAt) {
        this.id = id;
        this.bookingReference = bookingReference;
        this.travelerId = travelerId;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.gatewayType = gatewayType;
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewayOrderId = gatewayOrderId;
        this.gatewayRefundId = gatewayRefundId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.attempts = new ArrayList<>(attempts);
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.authorizedAt = authorizedAt;
        this.capturedAt = capturedAt;
        this.failedAt = failedAt;
        this.cancelledAt = cancelledAt;
        this.expiredAt = expiredAt;
    }

    void applyMutableState(String gatewayPaymentId, String gatewayOrderId, String gatewayRefundId, String status,
                           List<PaymentAttemptEmbeddable> attempts, Instant authorizedAt, Instant capturedAt,
                           Instant failedAt, Instant cancelledAt, Instant expiredAt) {
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewayOrderId = gatewayOrderId;
        this.gatewayRefundId = gatewayRefundId;
        this.status = status;
        this.attempts.clear();
        this.attempts.addAll(attempts);
        this.authorizedAt = authorizedAt;
        this.capturedAt = capturedAt;
        this.failedAt = failedAt;
        this.cancelledAt = cancelledAt;
        this.expiredAt = expiredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingReference() {
        return bookingReference;
    }

    public UUID getTravelerId() {
        return travelerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMethod() {
        return method;
    }

    public String getGatewayType() {
        return gatewayType;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public String getGatewayRefundId() {
        return gatewayRefundId;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<PaymentAttemptEmbeddable> getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public long getVersion() {
        return version;
    }
}
