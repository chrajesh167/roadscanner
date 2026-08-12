package com.roadscanner.notificationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One attempt to notify one person about one event on one channel — the row that makes delivery
 * idempotent and auditable.
 *
 * <p>Its identity is {@code (eventId, channel)}, not its own id. That triple is what an
 * at-least-once redelivery repeats, so claiming it before sending is what stops a redelivered
 * event becoming a second email.
 *
 * <p>The recipient is stored in full — it has to be, to answer "where did this actually go" when a
 * customer says they received nothing — but it is only ever <em>rendered</em> through
 * {@link Recipient#masked()}.
 */
public final class NotificationRecord {

    private final UUID id;
    private final UUID eventId;
    private final UUID bookingId;
    private final NotificationType type;
    private final Recipient recipient;
    private NotificationStatus status;
    private String failureReason;
    private final Instant createdAt;
    private Instant sentAt;

    private NotificationRecord(UUID id, UUID eventId, UUID bookingId, NotificationType type, Recipient recipient,
                                NotificationStatus status, String failureReason, Instant createdAt, Instant sentAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.recipient = Objects.requireNonNull(recipient, "recipient must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.failureReason = failureReason;
        this.sentAt = sentAt;
    }

    /** A claim on this (event, channel) pair, written before any send is attempted. */
    public static NotificationRecord pending(UUID eventId, UUID bookingId, NotificationType type,
                                              Recipient recipient, Instant now) {
        return new NotificationRecord(UUID.randomUUID(), eventId, bookingId, type, recipient,
                NotificationStatus.PENDING, null, now, null);
    }

    public static NotificationRecord reconstitute(UUID id, UUID eventId, UUID bookingId, NotificationType type,
                                                   Recipient recipient, NotificationStatus status, String failureReason,
                                                   Instant createdAt, Instant sentAt) {
        return new NotificationRecord(id, eventId, bookingId, type, recipient, status, failureReason, createdAt, sentAt);
    }

    /**
     * @param achieved what the channel actually managed — {@link NotificationStatus#SENT} for a
     *                 real provider, {@link NotificationStatus#DEMO_RECORDED} for a stand-in. The
     *                 adapter decides; this method records what it was told rather than assuming.
     */
    public void markDelivered(NotificationStatus achieved, Instant now) {
        if (achieved != NotificationStatus.SENT && achieved != NotificationStatus.DEMO_RECORDED) {
            throw new IllegalArgumentException("delivery status must be SENT or DEMO_RECORDED, got " + achieved);
        }
        this.status = achieved;
        this.sentAt = now;
        this.failureReason = null;
    }

    /** Truncated because a provider's error text can be arbitrarily long and this column is a
     * diagnostic, not a transcript. */
    public void markFailed(String reason, Instant now) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason == null ? "unknown" : reason.substring(0, Math.min(reason.length(), 500));
        this.sentAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public NotificationType type() {
        return type;
    }

    public Recipient recipient() {
        return recipient;
    }

    public NotificationStatus status() {
        return status;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> sentAt() {
        return Optional.ofNullable(sentAt);
    }
}
