package com.roadscanner.notificationservice.domain.model;

/** How a notification reaches the traveler. Part of the idempotency identity: the same event may
 * legitimately be delivered once per channel, and never twice on the same one. */
public enum NotificationChannel {
    EMAIL,
    SMS
}
