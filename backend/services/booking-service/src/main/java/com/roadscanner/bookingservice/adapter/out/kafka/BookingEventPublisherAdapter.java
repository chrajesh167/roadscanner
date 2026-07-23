package com.roadscanner.bookingservice.adapter.out.kafka;

import com.roadscanner.bookingservice.config.BookingProperties;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Implements {@link BookingEventPublisher}. A publish failure is logged, not thrown — the
 * Postgres write (already durable by the time any of these methods is called) is what makes the
 * fact durable; a lost Kafka publish loses only the async fan-out
 * (docs/services/booking-service/events-published.md). */
@Component
class BookingEventPublisherAdapter implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisherAdapter.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BookingProperties properties;

    BookingEventPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate, BookingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publishBookingCreated(Booking booking, Instant occurredAt) {
        publish(BookingEventType.CREATED, booking, occurredAt);
    }

    @Override
    public void publishBookingConfirmed(Booking booking, Instant occurredAt) {
        publish(BookingEventType.CONFIRMED, booking, occurredAt);
    }

    @Override
    public void publishBookingCancelled(Booking booking, Instant occurredAt) {
        publish(BookingEventType.CANCELLED, booking, occurredAt);
    }

    private void publish(BookingEventType eventType, Booking booking, Instant occurredAt) {
        BookingEventMessage message = new BookingEventMessage(eventType, booking.id().value(), booking.travelerId(),
                booking.tripId().value(), booking.status().name(),
                booking.cancellationReason().map(Enum::name).orElse(null), occurredAt);
        String topic = properties.kafka().bookingEventsTopic();
        kafkaTemplate.send(topic, booking.id().value().toString(), message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Failed to publish {} to topic {}", eventType, topic, ex);
            }
        });
    }
}
