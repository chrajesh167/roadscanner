package com.roadscanner.paymentservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One attempt to pay a {@link Payment} through the gateway. A retry after a failure is a new
 * {@code PaymentAttempt} on the same {@code Payment} (docs/architecture/payment-flow.md's
 * "Failure": "a retry is a new payment attempt against the same pending booking, not a new
 * booking"). Entity within the {@link Payment} aggregate; identity is {@link PaymentAttemptId}.
 */
public final class PaymentAttempt {

    private final PaymentAttemptId id;
    private final int attemptNumber;
    private final GatewayReference gatewayReference;
    private AttemptOutcome outcome;
    private String failureCode;
    private String failureReason;
    private final Instant startedAt;
    private Instant settledAt;

    private PaymentAttempt(PaymentAttemptId id, int attemptNumber, GatewayReference gatewayReference,
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

    static PaymentAttempt start(int attemptNumber, GatewayReference gatewayReference, Instant startedAt) {
        Objects.requireNonNull(gatewayReference, "gatewayReference must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        return new PaymentAttempt(PaymentAttemptId.generate(), attemptNumber, gatewayReference,
                AttemptOutcome.IN_PROGRESS, null, null, startedAt, null);
    }

    public static PaymentAttempt reconstitute(PaymentAttemptId id, int attemptNumber,
                                              GatewayReference gatewayReference, AttemptOutcome outcome,
                                              String failureCode, String failureReason, Instant startedAt,
                                              Instant settledAt) {
        return new PaymentAttempt(id, attemptNumber, gatewayReference, outcome, failureCode, failureReason,
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

    void timeOut(Instant settledAt) {
        this.outcome = AttemptOutcome.TIMED_OUT;
        this.settledAt = settledAt;
    }

    public PaymentAttemptId id() {
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
