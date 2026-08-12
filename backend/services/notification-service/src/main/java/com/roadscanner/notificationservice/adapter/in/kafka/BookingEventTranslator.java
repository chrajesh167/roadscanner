package com.roadscanner.notificationservice.adapter.in.kafka;

import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationType;
import com.roadscanner.notificationservice.domain.model.Recipient;

import java.util.Optional;

/**
 * Decides whether a booking event is worth telling the traveler about, what to call it, and on
 * which channel.
 *
 * <h2>Why a payment failure is read from this topic and not from {@code payment-events}</h2>
 * A declined payment produces two events: {@code payment-events/FAILED} and, because
 * {@code booking-service} cancels the booking in response, {@code booking-events/CANCELLED} with
 * {@code cancellationReason = PAYMENT_FAILED}. Consuming both topics would send the customer two
 * messages about one incident, and would need cross-topic suppression to avoid it.
 *
 * <p>This service consumes only {@code booking-events}, which resolves that structurally rather
 * than by suppression: the cancellation event already states the cause, and — decisively — it is
 * the only one of the two that carries a recipient. {@code payment-events} has no contact details
 * and cannot have any: {@code Contact} belongs to the {@code Booking} aggregate, and
 * {@code payment-service} has never held it.
 *
 * <h2>The mapping</h2>
 * <ul>
 *   <li>{@code CONFIRMED} → {@link NotificationType#BOOKING_CONFIRMED}</li>
 *   <li>{@code CANCELLED} + {@code PAYMENT_FAILED}/{@code PAYMENT_TIMED_OUT} →
 *       {@link NotificationType#PAYMENT_FAILED}, and never a cancellation notice as well</li>
 *   <li>{@code CANCELLED} + any other reason → {@link NotificationType#BOOKING_CANCELLED}</li>
 *   <li>{@code CREATED} → nothing. A booking awaiting payment is a step in a flow the traveler is
 *       still inside; mailing them about it is noise.</li>
 * </ul>
 *
 * <p>{@code HOLD_EXPIRED}, {@code TRIP_CANCELLED} and {@code PROVIDER_CONFIRMATION_FAILED} all
 * notify as cancellations. Each is a real cancellation the traveler is unaware of, and no other
 * event on this platform tells them — staying silent would be the worse failure.
 */
class BookingEventTranslator {

    private static final String EVENT_CONFIRMED = "CONFIRMED";
    private static final String EVENT_CANCELLED = "CANCELLED";
    private static final String REASON_PAYMENT_FAILED = "PAYMENT_FAILED";
    private static final String REASON_PAYMENT_TIMED_OUT = "PAYMENT_TIMED_OUT";

    /** Empty when this event should produce no notification, or cannot produce a usable one. */
    Optional<BookingNotification> translate(BookingEventMessage message) {
        Optional<NotificationType> type = notificationType(message);
        if (type.isEmpty()) {
            return Optional.empty();
        }

        // An event with no id cannot be de-duplicated, and an event with no booking or timestamp
        // cannot be recorded. Rather than invent any of them, this declines the event — sending
        // without an idempotency key risks a repeat on the next redelivery.
        if (message.eventId() == null || message.bookingId() == null || message.occurredAt() == null) {
            return Optional.empty();
        }

        return resolveRecipient(message).map(recipient -> new BookingNotification(
                message.eventId(),
                message.bookingId(),
                type.get(),
                recipient,
                message.bookingReference(),
                message.departureTime(),
                message.fareAmount(),
                message.fareCurrency(),
                message.occurredAt()));
    }

    private Optional<NotificationType> notificationType(BookingEventMessage message) {
        if (EVENT_CONFIRMED.equals(message.eventType())) {
            return Optional.of(NotificationType.BOOKING_CONFIRMED);
        }
        if (!EVENT_CANCELLED.equals(message.eventType())) {
            return Optional.empty();
        }
        String reason = message.cancellationReason();
        if (REASON_PAYMENT_FAILED.equals(reason) || REASON_PAYMENT_TIMED_OUT.equals(reason)) {
            return Optional.of(NotificationType.PAYMENT_FAILED);
        }
        return Optional.of(NotificationType.BOOKING_CANCELLED);
    }

    /**
     * Honours the traveler's stated preference exactly: {@code SMS} means SMS only, anything else
     * means email. Sending on both would ignore a choice they were explicitly asked to make.
     *
     * <p>Falls back to the other channel only when the preferred one has no address on the booking,
     * which beats recording a failure for a customer who is perfectly reachable.
     */
    private Optional<Recipient> resolveRecipient(BookingEventMessage message) {
        boolean prefersSms = "SMS".equalsIgnoreCase(message.communicationPreference());
        String phone = blankToNull(message.contactPhone());
        String email = blankToNull(message.contactEmail());

        if (prefersSms && phone != null) {
            return Optional.of(new Recipient(NotificationChannel.SMS, phone));
        }
        if (email != null) {
            return Optional.of(new Recipient(NotificationChannel.EMAIL, email));
        }
        return phone == null ? Optional.empty() : Optional.of(new Recipient(NotificationChannel.SMS, phone));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
