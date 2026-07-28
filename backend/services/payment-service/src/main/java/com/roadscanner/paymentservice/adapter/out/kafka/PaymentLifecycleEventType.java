package com.roadscanner.paymentservice.adapter.out.kafka;

/** The discriminator on the {@code payment-lifecycle-events} topic — the informational and refund
 * events {@code booking-service} does not consume (docs/services/payment-service/events-published.md).
 * {@code CREATED} is informational only; the refund events are consumed (in future) by
 * {@code notification-service} and {@code analytics-service}. */
public enum PaymentLifecycleEventType {
    CREATED,
    REFUND_INITIATED,
    REFUND_COMPLETED,
    REFUND_FAILED
}
