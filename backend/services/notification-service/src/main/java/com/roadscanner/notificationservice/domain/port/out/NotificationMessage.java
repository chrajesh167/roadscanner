package com.roadscanner.notificationservice.domain.port.out;

import java.util.Objects;

/**
 * A rendered message, ready for whichever channel is carrying it.
 *
 * <p>{@code subject} is meaningful to email and meaningless to SMS; rather than two shapes, SMS
 * adapters ignore it. One shape keeps the composing code channel-agnostic, which is the point of
 * having ports at all.
 */
public record NotificationMessage(String subject, String body) {

    public NotificationMessage {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
    }
}
