package com.roadscanner.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This service's own configuration.
 *
 * <p>Every value is supplied by the environment. Nothing here has a credential as a default, and
 * the {@code from} address is the only recipient-shaped value in the whole service — actual
 * recipients come from the booking event, never from configuration.
 */
@ConfigurationProperties(prefix = "roadscanner.notification")
public record NotificationProperties(Kafka kafka, Email email) {

    public record Kafka(String bookingEventsTopic) {
    }

    /**
     * @param host blank when no mail server is configured, which selects the adapter that records a
     *             failure rather than one that pretends to send
     * @param from the sender identity, e.g. {@code RoadScanner <no-reply@example.com>}
     */
    public record Email(String host, String from) {

        public boolean isConfigured() {
            return host != null && !host.isBlank();
        }
    }
}
