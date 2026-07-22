package com.roadscanner.inventoryservice.adapter.out.kafka;

import com.roadscanner.inventoryservice.config.InventoryProperties;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Implements {@link CatalogEventPublisher}. A publish failure is logged, not thrown — matching
 * {@code provider-integration-service}'s {@code KafkaAuditPublisherAdapter} rationale: the
 * Postgres write (already durable by the time any of these methods is called) is what makes the
 * fact durable; a lost Kafka publish loses only the async fan-out, not the fact itself. */
@Component
class CatalogEventPublisherAdapter implements CatalogEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventPublisherAdapter.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InventoryProperties properties;

    CatalogEventPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate, InventoryProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publishTripPublished(Trip trip, Instant occurredAt) {
        publishTripEvent(CatalogTripEventType.PUBLISHED, trip, occurredAt);
    }

    @Override
    public void publishTripUpdated(Trip trip, Instant occurredAt) {
        publishTripEvent(CatalogTripEventType.UPDATED, trip, occurredAt);
    }

    @Override
    public void publishTripCancelled(Trip trip, Instant occurredAt) {
        publishTripEvent(CatalogTripEventType.CANCELLED, trip, occurredAt);
    }

    private void publishTripEvent(CatalogTripEventType eventType, Trip trip, Instant occurredAt) {
        CatalogTripEventMessage message = new CatalogTripEventMessage(eventType, trip.id().value(),
                trip.operatorId().orElse(null), trip.operatorDisplayName(), trip.origin(), trip.destination(),
                trip.schedule().departureTime(), trip.schedule().arrivalTime(), trip.busTypeCategory(),
                trip.amenities(), trip.fare().amount(), trip.fare().currency().getCurrencyCode(), occurredAt);
        send(properties.kafka().catalogTripEventsTopic(), trip.id().value().toString(), message);
    }

    @Override
    public void publishOperatorUpdated(UUID operatorId, String displayName, Instant occurredAt) {
        send(properties.kafka().catalogOperatorEventsTopic(), operatorId.toString(),
                new OperatorUpdatedMessage(operatorId, displayName, occurredAt));
    }

    @Override
    public void publishCatalogSyncCompleted(ProviderType providerType, int tripsReconciled, long catalogVersion, Instant occurredAt) {
        send(properties.kafka().catalogSyncEventsTopic(), providerType.code(),
                new CatalogSyncMessage("CatalogSyncCompleted", providerType.code(), tripsReconciled, catalogVersion,
                        null, occurredAt));
    }

    @Override
    public void publishCatalogSyncFailed(ProviderType providerType, String errorDetail, Instant occurredAt) {
        send(properties.kafka().catalogSyncEventsTopic(), providerType.code(),
                new CatalogSyncMessage("CatalogSyncFailed", providerType.code(), null, null, errorDetail, occurredAt));
    }

    private void send(String topic, String key, Object message) {
        kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Failed to publish {} to topic {}", message.getClass().getSimpleName(), topic, ex);
            }
        });
    }
}
