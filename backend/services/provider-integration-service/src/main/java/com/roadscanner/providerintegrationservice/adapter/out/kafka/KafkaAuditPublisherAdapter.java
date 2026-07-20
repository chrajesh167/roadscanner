package com.roadscanner.providerintegrationservice.adapter.out.kafka;

import com.roadscanner.providerintegrationservice.config.ProviderProperties;
import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes to {@code provider-integration-events}, keyed by provider type so every event about
 * the same provider lands in the same partition and stays ordered relative to itself — matching
 * the platform's general Kafka ordering rule (docs/architecture/event-catalog.md).
 *
 * A publish failure here is logged, not thrown — {@code AuditRecordRepository}'s write (the
 * Postgres side of the same event, always performed first by every caller) is what makes this
 * event durable; a lost Kafka publish loses only the async fan-out to future consumers, not the
 * event itself.
 */
@Component
class KafkaAuditPublisherAdapter implements AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditPublisherAdapter.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProviderProperties properties;

    KafkaAuditPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate, ProviderProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(AuditRecord record) {
        String sessionId = record.sessionIdIfPresent().map(Object::toString).orElse(null);
        ProviderAuditMessage message = new ProviderAuditMessage(record.eventType().name(), record.providerType().code(),
                sessionId, record.message(), record.occurredAt());

        kafkaTemplate.send(properties.kafka().auditTopic(), record.providerType().code(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish {} for provider {} to Kafka", record.eventType(),
                                record.providerType(), ex);
                    }
                });
    }
}
