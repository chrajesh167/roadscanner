package com.roadscanner.bookingservice.adapter.in.event;

/** The discriminator {@code payment-service} (not yet built) is expected to publish on its
 * payment-events topic (docs/architecture/event-catalog.md's "Payment Events"). */
public enum PaymentEventType {
    COMPLETED,
    FAILED,
    TIMED_OUT
}
