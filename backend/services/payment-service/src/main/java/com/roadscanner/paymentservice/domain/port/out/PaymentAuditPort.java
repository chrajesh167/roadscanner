package com.roadscanner.paymentservice.domain.port.out;

import java.time.Instant;

/** Insert-only audit trail for security-sensitive events — webhook received, signature
 * verified/rejected, refund initiated, payment captured — backing FR-8.3 and NFR-13
 * (docs/services/payment-service/responsibilities.md). */
public interface PaymentAuditPort {

    void record(String eventType, String subjectReference, String detail, Instant occurredAt);
}
