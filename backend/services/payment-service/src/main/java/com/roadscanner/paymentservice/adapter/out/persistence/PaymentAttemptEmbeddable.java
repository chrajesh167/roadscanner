package com.roadscanner.paymentservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;
import java.util.UUID;

/** Persistence shape for a {@code PaymentAttempt}, embedded in {@code payment_attempts}. */
@Embeddable
public class PaymentAttemptEmbeddable {

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "gateway_refund_id")
    private String gatewayRefundId;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected PaymentAttemptEmbeddable() {
    }

    PaymentAttemptEmbeddable(UUID attemptId, int attemptNumber, String gatewayPaymentId, String gatewayOrderId,
                             String gatewayRefundId, String outcome, String failureCode, String failureReason,
                             Instant startedAt, Instant settledAt) {
        this.attemptId = attemptId;
        this.attemptNumber = attemptNumber;
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewayOrderId = gatewayOrderId;
        this.gatewayRefundId = gatewayRefundId;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.startedAt = startedAt;
        this.settledAt = settledAt;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
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

    public String getOutcome() {
        return outcome;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
