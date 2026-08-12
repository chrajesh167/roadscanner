package com.roadscanner.notificationservice.domain.port.out;

import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.Recipient;

/**
 * Sends a message by SMS.
 *
 * <p>Returns the status it achieved rather than {@code void}, which email does not need to. An SMS
 * adapter may legitimately be a stand-in with no carrier behind it, and the difference between
 * "a carrier accepted this" and "this was recorded for a demo" has to survive into the log — a
 * {@code void} method would force the caller to assume {@link NotificationStatus#SENT} and record
 * a delivery that never happened.
 */
public interface SmsNotificationPort {

    /**
     * @return {@link NotificationStatus#SENT} when a real provider accepted the message, or
     *         {@link NotificationStatus#DEMO_RECORDED} when it was recorded without one
     * @throws com.roadscanner.notificationservice.domain.exception.NotificationDeliveryException
     *         when the message could not be handed to the provider
     */
    NotificationStatus send(Recipient recipient, NotificationMessage message);
}
