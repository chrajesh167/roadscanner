package com.roadscanner.paymentservice.adapter.out.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The wire shape published to {@code payment-lifecycle-events} — {@code PaymentCreated} (keyed by
 * payment) and the refund events. {@code refundId} is null for {@code CREATED}. */
public record PaymentLifecycleEventMessage(PaymentLifecycleEventType eventType, UUID paymentId, UUID bookingReference,
                                           UUID travelerId, BigDecimal amount, String currency, UUID refundId,
                                           String status, Instant occurredAt) {
}
