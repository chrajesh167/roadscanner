package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.Booking;

import java.time.Instant;

/** Publishes {@code BookingCreated}/{@code BookingConfirmed}/{@code BookingCancelled} on the
 * {@code booking-events} topic (docs/services/booking-service/events-published.md). A publish
 * failure is logged, not thrown — matching {@code provider-integration-service}'s and
 * {@code inventory-service}'s identical rationale: the Postgres write (already durable by the
 * time any of these methods is called) is what makes the fact durable; a lost Kafka publish
 * loses only the async fan-out, not the fact itself. */
public interface BookingEventPublisher {

    void publishBookingCreated(Booking booking, Instant occurredAt);

    void publishBookingConfirmed(Booking booking, Instant occurredAt);

    void publishBookingCancelled(Booking booking, Instant occurredAt);
}
