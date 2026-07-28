package com.roadscanner.paymentservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One attempt to execute a {@link Refund} through the gateway. Crucially, a failed refund does
 * <strong>not</strong> trigger unbounded automatic retries — per docs/architecture/payment-flow.md
 * ("a failed refund is not silently retried forever ... routes to support"), a failed refund is
 * surfaced for manual intervention, not looped. Entity within the {@link Refund} aggregate.
 */
public final class RefundAttempt {

    private final RefundAttemptId id;
    private final int attemptNumber;
    private final GatewayReference gatewayReference;
    private AttemptOutcome outcome;
    private String failureCode;
    private String failureReason;
    private final Instant startedAt;
    private Instant settledAt;

    private RefundAttempt(RefundAttemptId id, int attemptNumber, GatewayReference gatewayReference,
                          AttemptOutcome outcome, String failureCode, String failureReason,
                          Instant startedAt, Instant settledAt) {
        this.id = id;
        this.attemptNumber = attemptNumber;
        this.gatewayReference = gatewayReference;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.startedAt = startedAt;
        this.settledAt = settledAt;
    }

    static RefundAttempt start(int attemptNumber, GatewayReference gatewayReference, Instant startedAt) {
        Objects.requireNonNull(gatewayReference, "gatewayReference must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        return new RefundAttempt(RefundAttemptId.generate(), attemptNumber, gatewayReference,
                AttemptOutcome.IN_PROGRESS, null, null, startedAt, null);
    }

    public static RefundAttempt reconstitute(RefundAttemptId id, int attemptNumber,
                                             GatewayReference gatewayReference, AttemptOutcome outcome,
                                             String failureCode, String failureReason, Instant startedAt,
                                             Instant settledAt) {
        return new RefundAttempt(id, attemptNumber, gatewayReference, outcome, failureCode, failureReason,
                startedAt, settledAt);
    }

    void succeed(Instant settledAt) {
        this.outcome = AttemptOutcome.SUCCEEDED;
        this.settledAt = settledAt;
    }

    void fail(String failureCode, String failureReason, Instant settledAt) {
        this.outcome = AttemptOutcome.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.settledAt = settledAt;
    }

    public RefundAttemptId id() {
        return id;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public GatewayReference gatewayReference() {
        return gatewayReference;
    }

    public AttemptOutcome outcome() {
        return outcome;
    }

    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> settledAt() {
        return Optional.ofNullable(settledAt);
    }
}
