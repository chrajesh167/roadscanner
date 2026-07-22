package com.roadscanner.inventoryservice.adapter.in.event;

import com.roadscanner.inventoryservice.domain.port.in.IngestCancelledTrip;
import com.roadscanner.inventoryservice.domain.port.in.IngestPublishedTrip;
import com.roadscanner.inventoryservice.domain.port.in.IngestUpdatedTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes {@code operator-service}'s trip-events topic
 * (docs/services/inventory-service/events-consumed.md), dispatching on
 * {@link OperatorTripEventMessage#eventType()} to the corresponding inbound port — matching
 * {@code search-service}'s {@code TripEventListener} dispatch pattern exactly. Malformed input
 * propagates to the container's error handler (retry-then-dead-letter,
 * {@code config.KafkaConfig}), the same one-mapping-layer philosophy applied to Kafka. */
@Component
class TripEventListener {

    private static final Logger log = LoggerFactory.getLogger(TripEventListener.class);

    private final IngestPublishedTrip ingestPublishedTrip;
    private final IngestUpdatedTrip ingestUpdatedTrip;
    private final IngestCancelledTrip ingestCancelledTrip;

    TripEventListener(IngestPublishedTrip ingestPublishedTrip, IngestUpdatedTrip ingestUpdatedTrip,
                       IngestCancelledTrip ingestCancelledTrip) {
        this.ingestPublishedTrip = ingestPublishedTrip;
        this.ingestUpdatedTrip = ingestUpdatedTrip;
        this.ingestCancelledTrip = ingestCancelledTrip;
    }

    @KafkaListener(id = "trip-events-listener", topics = "${roadscanner.inventory.kafka.operator-trip-events-topic}",
            containerFactory = "operatorTripEventListenerContainerFactory")
    void onMessage(OperatorTripEventMessage message) {
        log.debug("Received {} for trip {}", message.eventType(), message.tripId());
        switch (message.eventType()) {
            case PUBLISHED -> ingestPublishedTrip.ingest(new IngestPublishedTrip.Command(
                    message.tripId(), message.operatorId(), message.operatorName(), message.origin(),
                    message.destination(), message.departureTime(), message.arrivalTime(), message.busTypeCategory(),
                    message.amenities(), message.fareAmount(), message.fareCurrency(),
                    message.seatLayout().stream()
                            .map(s -> new IngestPublishedTrip.SeatEntry(s.seatNumber(), s.deck(), s.seatType(),
                                    s.wheelchairAccessible(), s.position()))
                            .toList(),
                    message.occurredAt()));
            case UPDATED -> ingestUpdatedTrip.ingest(new IngestUpdatedTrip.Command(
                    message.tripId(), message.origin(), message.destination(), message.departureTime(),
                    message.arrivalTime(), message.operatorName(), message.busTypeCategory(), message.amenities(),
                    message.fareAmount(), message.fareCurrency(), message.occurredAt()));
            case CANCELLED -> ingestCancelledTrip.ingest(new IngestCancelledTrip.Command(
                    message.tripId(), message.occurredAt()));
        }
    }
}
