package com.roadscanner.paymentservice.adapter.out.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The wire shape published to {@code payment-events}. The first four components
 * ({@code eventType}, {@code bookingId}, {@code paymentReference}, {@code occurredAt}) match
 * {@code booking-service}'s frozen {@code PaymentEventMessage} field-for-field, so its consumer
 * deserializes this without change (docs/services/booking-service/events-consumed.md). The trailing
 * {@code paymentId}/{@code amount}/{@code currency} are analytics enrichment {@code booking-service}
 * safely ignores ({@code FAIL_ON_UNKNOWN_PROPERTIES} disabled on its side, as here).
 */
public record PaymentEventMessage(PaymentEventType eventType, UUID bookingId, String paymentReference,
                                  Instant occurredAt, UUID paymentId, BigDecimal amount, String currency) {
}
