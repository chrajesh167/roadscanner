package com.roadscanner.notificationservice.domain.port.in;

import com.roadscanner.notificationservice.domain.model.BookingNotification;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Delivers one notification for one booking event.
 *
 * <p>Never throws for a delivery failure. The caller is a Kafka listener, and an exception there
 * means redelivery — which for a message that already reached the customer would mean sending it
 * again. Failures are recorded on the returned record instead.
 */
public interface SendBookingNotification {

    /**
     * @return the record written, or empty when this (event, channel) pair was already handled and
     *         nothing was sent
     */
    Optional<NotificationRecord> send(Command command);

    record Command(BookingNotification notification) {
        public Command {
            Objects.requireNonNull(notification, "notification must not be null");
        }
    }
}
