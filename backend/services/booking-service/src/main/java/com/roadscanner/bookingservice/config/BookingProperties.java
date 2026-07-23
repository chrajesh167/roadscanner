package com.roadscanner.bookingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Operational tuning knobs (docs/services/booking-service/). Values live in
 * {@code application.yml}, overridable per environment. */
@ConfigurationProperties(prefix = "roadscanner.booking")
public record BookingProperties(Kafka kafka, Scheduling scheduling) {

    public record Kafka(
            String catalogTripEventsTopic,
            String providerIntegrationEventsTopic,
            String paymentEventsTopic,
            String bookingEventsTopic
    ) {
    }

    /** Cron expressions for the two scheduled use cases (docs/services/booking-service/use-cases.md's
     * "Complete Booking" and "Sweep Stale Holds"). */
    public record Scheduling(String completeBookingCron, String sweepStaleHoldsCron) {
    }
}
