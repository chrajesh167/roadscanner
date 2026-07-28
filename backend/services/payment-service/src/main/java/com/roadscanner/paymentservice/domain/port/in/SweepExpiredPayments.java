package com.roadscanner.paymentservice.domain.port.in;

/**
 * {@code Sweep Expired Payments} — transitions pre-capture payments past their acceptable window to
 * {@code EXPIRED} and emits {@code PaymentTimedOut} (docs/architecture/payment-flow.md's "Timeout";
 * docs/services/payment-service/use-cases.md).
 */
public interface SweepExpiredPayments {

    Result sweep();

    record Result(int expiredCount) {
    }
}
