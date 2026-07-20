package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One durable audit-trail entry, written whenever an {@link AuditEventType} occurs — the
 * persisted counterpart of the Kafka message {@code AuditPublisher} emits for the same event
 * (see {@code KafkaAuditPublisherAdapter}). Kept even though nothing in this service reads it
 * back today: an audit trail exists to be queried by operators/compliance tooling directly
 * against this service's own database, not necessarily through its own API. */
public record AuditRecord(UUID id, ProviderType providerType, AuditEventType eventType, ProviderSessionId sessionId,
                           String message, Instant occurredAt) {

    public AuditRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AuditRecord of(ProviderType providerType, AuditEventType eventType, ProviderSessionId sessionId,
                                  String message, Instant occurredAt) {
        return new AuditRecord(UUID.randomUUID(), providerType, eventType, sessionId, message, occurredAt);
    }

    public Optional<ProviderSessionId> sessionIdIfPresent() {
        return Optional.ofNullable(sessionId);
    }
}
