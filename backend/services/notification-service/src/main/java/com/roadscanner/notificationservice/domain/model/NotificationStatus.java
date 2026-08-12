package com.roadscanner.notificationservice.domain.model;

/**
 * Where a notification got to.
 *
 * <p>{@code SENT} means a channel adapter accepted it — for email, that the SMTP server took the
 * message. It is not proof of inbox delivery, which no sender can observe, and nothing in this
 * service claims otherwise.
 *
 * <p>{@code DEMO_RECORDED} is the mock SMS outcome and exists precisely so it cannot be mistaken
 * for {@code SENT}. A demo adapter reporting {@code SENT} would put a row in the log asserting a
 * carrier delivered a message that was never handed to one.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    DEMO_RECORDED,
    FAILED
}
