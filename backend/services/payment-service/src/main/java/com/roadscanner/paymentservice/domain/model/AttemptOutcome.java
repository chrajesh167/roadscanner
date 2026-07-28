package com.roadscanner.paymentservice.domain.model;

/** The outcome of a single {@link PaymentAttempt} or {@link RefundAttempt} against the gateway. */
public enum AttemptOutcome {
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    TIMED_OUT
}
