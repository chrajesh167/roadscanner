package com.roadscanner.paymentservice.domain.port.out;

import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.Refund;

import java.time.Instant;

/**
 * Publishes payment and refund events (docs/services/payment-service/events-published.md). The
 * business outcomes {@code booking-service} consumes ({@code PaymentCompleted}/{@code PaymentFailed}/
 * {@code PaymentTimedOut}) go to the {@code payment-events} topic in the shape
 * {@code booking-service}'s frozen consumer expects; the informational {@code PaymentCreated} and
 * the refund events go to a separate {@code payment-lifecycle-events} topic that
 * {@code booking-service} does not consume — see the adapter's Javadoc for why the topics are split
 * (booking-service's frozen {@code PaymentEventType} enum has only {@code COMPLETED}/{@code FAILED}/
 * {@code TIMED_OUT}).
 *
 * <p>A publish failure is logged, not thrown — the Postgres write (already durable) is what makes
 * the fact durable; a lost publish loses only the async fan-out (the platform-wide convention).
 */
public interface PaymentEventPublisher {

    /** Informational only — {@code booking-service} must never depend on it
     * (docs/services/payment-service/events-published.md). */
    void publishPaymentCreated(Payment payment, Instant occurredAt);

    void publishPaymentCompleted(Payment payment, Instant occurredAt);

    void publishPaymentFailed(Payment payment, Instant occurredAt);

    void publishPaymentTimedOut(Payment payment, Instant occurredAt);

    void publishRefundInitiated(Refund refund, Instant occurredAt);

    void publishRefundCompleted(Refund refund, Instant occurredAt);

    void publishRefundFailed(Refund refund, Instant occurredAt);
}
