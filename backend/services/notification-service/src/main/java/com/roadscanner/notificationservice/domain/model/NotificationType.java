package com.roadscanner.notificationservice.domain.model;

/**
 * What happened, in the traveler's terms rather than the publisher's.
 *
 * <p>Deliberately not a copy of {@code booking-events}' {@code eventType}. One booking event maps
 * to different notifications depending on why it happened: a {@code CANCELLED} caused by a declined
 * payment is a payment failure to the person reading it, not a cancellation notice. Keeping this
 * vocabulary separate is what lets the mapping express that.
 */
public enum NotificationType {
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    PAYMENT_FAILED
}
