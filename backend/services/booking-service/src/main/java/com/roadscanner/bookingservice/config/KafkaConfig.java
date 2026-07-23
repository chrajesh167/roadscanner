package com.roadscanner.bookingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.bookingservice.adapter.in.event.CatalogTripEventMessage;
import com.roadscanner.bookingservice.adapter.in.event.PaymentEventMessage;
import com.roadscanner.bookingservice.adapter.in.event.ProviderAuditMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring for both directions this service has: three explicit, type-bound consumer
 * factories for {@code inventory-service}'s catalog events, {@code provider-integration-service}'s
 * audit events, and {@code payment-service}'s (not yet real) payment events — matching
 * {@code inventory-service}'s {@code KafkaConfig} rationale exactly, since none of these upstream
 * producers can be assumed to emit Spring-Kafka-specific type headers — and one explicit producer
 * for this service's own {@code booking-events} topic, reading {@link KafkaConnectionDetails}
 * explicitly so Testcontainers' {@code @ServiceConnection} override is honored in tests.
 *
 * All three consumer factories share one {@link DefaultErrorHandler}: bounded retries, then a
 * per-topic dead-letter topic — the same "one mapping layer" philosophy
 * {@code GlobalExceptionHandler} applies to HTTP, applied here to Kafka.
 *
 * <p><strong>{@code missingTopicsFatal(false)} on every listener container, explicitly.</strong>
 * {@code catalog-trip-events} and {@code provider-integration-events} are real, already-shipped
 * topics, but {@code payment-events} has no real producer yet — {@code payment-service} doesn't
 * exist (docs/services/booking-service/boundaries.md's "Relationship to `payment-service`").
 * Spring Kafka's own default for a missing topic at container-startup time depends on the
 * broker's {@code auto.create.topics.enable} setting, which this service does not control and
 * should not have to reason about to start up reliably. Setting this explicitly means this
 * service's own startup is never gated on any upstream topic's existence, regardless of how the
 * broker is configured — found and fixed by final implementation audit, which flagged this as
 * reasoned-but-unverified rather than deterministic.
 */
@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
    }

    @Bean
    public ConsumerFactory<String, CatalogTripEventMessage> catalogTripEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(), typedJsonDeserializer(CatalogTripEventMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CatalogTripEventMessage> catalogTripEventListenerContainerFactory(
            ConsumerFactory<String, CatalogTripEventMessage> catalogTripEventConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, CatalogTripEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(catalogTripEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        allowMissingTopicsAtStartup(factory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, ProviderAuditMessage> providerAuditEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(), typedJsonDeserializer(ProviderAuditMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProviderAuditMessage> providerAuditEventListenerContainerFactory(
            ConsumerFactory<String, ProviderAuditMessage> providerAuditEventConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ProviderAuditMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(providerAuditEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        allowMissingTopicsAtStartup(factory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PaymentEventMessage> paymentEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(), typedJsonDeserializer(PaymentEventMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEventMessage> paymentEventListenerContainerFactory(
            ConsumerFactory<String, PaymentEventMessage> paymentEventConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        allowMissingTopicsAtStartup(factory);
        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> bookingEventProducerFactory(KafkaProperties kafkaProperties,
                                                                         KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducerBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> bookingEventProducerFactory) {
        return new KafkaTemplate<>(bookingEventProducerFactory);
    }

    private Map<String, Object> consumerProperties(KafkaProperties kafkaProperties,
                                                     KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumerBootstrapServers());
        return properties;
    }

    private <T> ErrorHandlingDeserializer<T> typedJsonDeserializer(Class<T> targetType, ObjectMapper objectMapper) {
        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(targetType, objectMapper, false);
        jsonDeserializer.addTrustedPackages(targetType.getPackageName());
        return new ErrorHandlingDeserializer<>(jsonDeserializer);
    }

    /** See the class Javadoc's "{@code missingTopicsFatal(false)} on every listener container,
     * explicitly" note. */
    private void allowMissingTopicsAtStartup(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        factory.getContainerProperties().setMissingTopicsFatal(false);
    }
}
