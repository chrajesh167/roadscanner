package com.roadscanner.bookingservice.adapter.in.event;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HandleTripCancelled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code inventory-service}'s merged-catalog trip-events topic
 * (docs/services/booking-service/events-consumed.md), acting only on {@code CANCELLED} —
 * {@code PUBLISHED}/{@code UPDATED} are logged and discarded, since neither changes anything
 * about an existing booking (this service re-reads current trip facts synchronously, on demand,
 * rather than maintaining a derived catalog copy). Malformed input propagates to the container's
 * error handler (retry-then-dead-letter, {@code config.KafkaConfig}), matching every other
 * service's Kafka listener in this codebase.
 */
@Component
class TripCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(TripCancelledListener.class);

    private final HandleTripCancelled handleTripCancelled;

    TripCancelledListener(HandleTripCancelled handleTripCancelled) {
        this.handleTripCancelled = handleTripCancelled;
    }

    @KafkaListener(id = "catalog-trip-events-listener",
            topics = "${roadscanner.booking.kafka.catalog-trip-events-topic}",
            containerFactory = "catalogTripEventListenerContainerFactory")
    void onMessage(CatalogTripEventMessage message) {
        if (message.eventType() != CatalogTripEventType.CANCELLED) {
            log.debug("Ignoring {} for trip {} — booking-service only reacts to TripCancelled",
                    message.eventType(), message.tripId());
            return;
        }
        handleTripCancelled.handle(new HandleTripCancelled.Command(new TripId(message.tripId()), message.occurredAt()));
    }
}
