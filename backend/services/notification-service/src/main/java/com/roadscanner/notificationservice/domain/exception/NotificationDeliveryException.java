package com.roadscanner.notificationservice.domain.exception;

/**
 * A channel could not deliver a message.
 *
 * <p>Expected, not exceptional: mail servers refuse, credentials expire, providers rate-limit. It
 * is caught by the application service, recorded against the notification, and goes no further —
 * a booking that already happened is not undone by a message that did not arrive.
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
