package com.roadscanner.notificationservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything this service knows about one thing that happened to one booking — the input to both
 * the channel decision and the message body.
 *
 * <p>Assembled from a single {@code booking-events} message. Nothing here is fetched: a
 * notification that had to call another service to be written would fail whenever that service was
 * down, which is the opposite of what an asynchronous fan-out is for.
 *
 * <p>Route (origin/destination) is absent, and its absence is deliberate rather than an oversight —
 * {@code booking-service} genuinely does not hold it. The templates are written to read correctly
 * without it rather than printing an empty arrow.
 */
public record BookingNotification(
        UUID eventId,
        UUID bookingId,
        NotificationType type,
        Recipient recipient,
        String bookingReference,
        Instant departureTime,
        BigDecimal fareAmount,
        String fareCurrency,
        Instant occurredAt) {

    public BookingNotification {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(recipient, "recipient must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public NotificationChannel channel() {
        return recipient.channel();
    }

    /** Absent until the provider confirms — a booking cancelled before that never had one. */
    public Optional<String> bookingReferenceIfPresent() {
        return Optional.ofNullable(bookingReference).filter(reference -> !reference.isBlank());
    }

    public Optional<Instant> departureTimeIfPresent() {
        return Optional.ofNullable(departureTime);
    }

    public boolean hasFare() {
        return fareAmount != null && fareCurrency != null && !fareCurrency.isBlank();
    }
}
