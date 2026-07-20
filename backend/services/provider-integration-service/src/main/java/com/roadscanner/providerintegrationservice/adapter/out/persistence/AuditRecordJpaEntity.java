package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Persistence shape for {@code AuditRecord} — insert-only, no {@code @Version}: an audit
 * record is never updated after it's written. */
@Entity
@Table(name = "audit_records")
public class AuditRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_type", nullable = false, updatable = false)
    private String providerType;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Column(name = "message", nullable = false, updatable = false)
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditRecordJpaEntity() {
    }

    AuditRecordJpaEntity(UUID id, String providerType, String eventType, UUID sessionId, String message,
                          Instant occurredAt) {
        this.id = id;
        this.providerType = providerType;
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
