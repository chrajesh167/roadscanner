package com.roadscanner.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/** Operational tuning knobs (docs/services/payment-service/). Values live in {@code application.yml},
 * overridable per environment. */
@ConfigurationProperties(prefix = "roadscanner.payment")
public record PaymentProperties(Duration paymentWindow, Kafka kafka, Scheduling scheduling, Gateways gateways) {

    /**
     * Two publish topics (docs/services/payment-service/events-published.md): {@code payment-events}
     * carries the business outcomes {@code booking-service} consumes ({@code COMPLETED}/
     * {@code FAILED}/{@code TIMED_OUT}); {@code payment-lifecycle-events} carries the informational
     * {@code PaymentCreated} and the refund events {@code booking-service} does not consume — the
     * split keeps this service off {@code booking-service}'s frozen 3-value consumer enum. The
     * consumed {@code booking-events} topic drives the {@code BookingCancelled} reconciliation.
     */
    public record Kafka(String paymentEventsTopic, String paymentLifecycleEventsTopic, String bookingEventsTopic) {
    }

    public record Scheduling(String sweepExpiredPaymentsCron) {
    }

    /** Per-gateway webhook signing secrets, keyed by {@code GatewayType} code, plus the default
     * gateway used when a payment-initiation request does not name one. */
    public record Gateways(String defaultType, Map<String, String> webhookSecrets) {

        public String webhookSecretFor(String code) {
            if (webhookSecrets == null) {
                return "";
            }
            return webhookSecrets.getOrDefault(code,
                    webhookSecrets.getOrDefault(code.toLowerCase(), webhookSecrets.getOrDefault(code.toUpperCase(), "")));
        }
    }
}
