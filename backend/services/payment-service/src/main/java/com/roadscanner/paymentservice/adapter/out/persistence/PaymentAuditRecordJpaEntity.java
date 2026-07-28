package com.roadscanner.paymentservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Insert-only security-sensitive audit trail (FR-8.3, NFR-13). */
@Entity
@Table(name = "payment_audit_records")
public class PaymentAuditRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "subject_reference", updatable = false)
    private String subjectReference;

    @Column(name = "detail", updatable = false, length = 1024)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PaymentAuditRecordJpaEntity() {
    }

    PaymentAuditRecordJpaEntity(UUID id, String eventType, String subjectReference, String detail, Instant occurredAt) {
        this.id = id;
        this.eventType = eventType;
        this.subjectReference = subjectReference;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }
}
