package com.roadscanner.paymentservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Insert-only reconciliation-discrepancy record, surfaced to support — never used to move money
 * (docs/services/payment-service/use-cases.md). */
@Entity
@Table(name = "reconciliation_records")
public class ReconciliationRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "kind", nullable = false, updatable = false)
    private String kind;

    @Column(name = "subject_reference", updatable = false)
    private String subjectReference;

    @Column(name = "detail", updatable = false, length = 1024)
    private String detail;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected ReconciliationRecordJpaEntity() {
    }

    ReconciliationRecordJpaEntity(UUID id, String kind, String subjectReference, String detail, Instant detectedAt) {
        this.id = id;
        this.kind = kind;
        this.subjectReference = subjectReference;
        this.detail = detail;
        this.detectedAt = detectedAt;
    }
}
