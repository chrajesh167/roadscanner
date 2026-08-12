package com.roadscanner.notificationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Persistence shape for {@code NotificationRecord} — zero compile-time dependency on
 * {@code domain.model}, matching every other JPA entity in this codebase's family. */
@Entity
@Table(name = "notification_log")
public class NotificationLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationLogJpaEntity() {
    }

    NotificationLogJpaEntity(UUID id, UUID eventId, UUID bookingId, String eventType, String channel,
                             String recipient, String status, String failureReason, Instant createdAt,
                             Instant sentAt) {
        this.id = id;
        this.eventId = eventId;
        this.bookingId = bookingId;
        this.eventType = eventType;
        this.channel = channel;
        this.recipient = recipient;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    void apply(String status, String failureReason, Instant sentAt) {
        this.status = status;
        this.failureReason = failureReason;
        this.sentAt = sentAt;
    }

    UUID getId() {
        return id;
    }

    UUID getEventId() {
        return eventId;
    }

    UUID getBookingId() {
        return bookingId;
    }

    String getEventType() {
        return eventType;
    }

    String getChannel() {
        return channel;
    }

    String getRecipient() {
        return recipient;
    }

    String getStatus() {
        return status;
    }

    String getFailureReason() {
        return failureReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getSentAt() {
        return sentAt;
    }
}
