package com.roadscanner.paymentservice.adapter.out.kafka;

/**
 * The discriminator on the {@code payment-events} topic — <strong>exactly</strong> the three values
 * {@code booking-service}'s frozen consumer enum defines
 * ({@code booking-service}'s {@code adapter.in.event.PaymentEventType}:
 * {@code COMPLETED}/{@code FAILED}/{@code TIMED_OUT}). This topic carries only these three business
 * outcomes; the informational {@code PaymentCreated} and the refund events go to
 * {@code payment-lifecycle-events} so a value outside this enum never reaches {@code booking-service}.
 */
public enum PaymentEventType {
    COMPLETED,
    FAILED,
    TIMED_OUT
}
