package com.roadscanner.notificationservice.domain.port.out;

import com.roadscanner.notificationservice.domain.model.Recipient;

/**
 * Sends a message by email.
 *
 * <p>Throws on failure rather than returning a boolean: the caller has to record a reason in the
 * notification log, and an exception carries one where a {@code false} does not. The application
 * service is the single place that catches it, so a delivery failure never escapes into the Kafka
 * listener and never becomes a consumer error.
 */
public interface EmailNotificationPort {

    /**
     * @throws com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException
     *         when the message could not be handed to the mail server
     */
    void send(Recipient recipient, NotificationMessage message);
}
