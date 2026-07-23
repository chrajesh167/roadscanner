package com.roadscanner.bookingservice.adapter.in.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The wire shape {@code payment-service} (not yet built) is expected to publish for
 * {@code PaymentCompleted}/{@code PaymentFailed}/{@code PaymentTimedOut}
 * (docs/architecture/event-catalog.md, docs/services/booking-service/events-consumed.md's
 * "Designed for, not yet real"). This service's own reasonable specification of the fields
 * {@code Handle Payment Completed}/{@code Failed}/{@code TimedOut} need — reconcile against the
 * real shape once {@code payment-service} exists.
 */
public record PaymentEventMessage(PaymentEventType eventType, UUID bookingId, String paymentReference,
                                   Instant occurredAt) {
}
