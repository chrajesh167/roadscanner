package com.roadscanner.inventoryservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.inventoryservice.adapter.in.event.OperatorOperatorEventMessage;
import com.roadscanner.inventoryservice.adapter.in.event.OperatorRouteEventMessage;
import com.roadscanner.inventoryservice.adapter.in.event.OperatorTripEventMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
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
 * factories for {@code operator-service}'s events (matching {@code search-service}'s
 * {@code KafkaConfig} rationale for interoperability — this consumer cannot assume
 * {@code operator-service} emits Spring-Kafka-specific type headers), and one explicit producer
 * for this service's own merged-catalog/operator/sync events (matching
 * {@code provider-integration-service}'s {@code KafkaConfig} rationale for reading
 * {@link KafkaConnectionDetails} explicitly, so Testcontainers' {@code @ServiceConnection}
 * override is honored in tests).
 *
 * All three consumer factories share one {@link DefaultErrorHandler}: bounded retries, then a
 * per-topic dead-letter topic — the same "one mapping layer" philosophy
 * {@code GlobalExceptionHandler} applies to HTTP, applied here to Kafka.
 */
@Configuration
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
    }

    @Bean
    public ConsumerFactory<String, OperatorTripEventMessage> operatorTripEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(), typedJsonDeserializer(OperatorTripEventMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OperatorTripEventMessage> operatorTripEventListenerContainerFactory(
            ConsumerFactory<String, OperatorTripEventMessage> operatorTripEventConsumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, OperatorTripEventMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(operatorTripEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, OperatorRouteEventMessage> operatorRouteEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(), typedJsonDeserializer(OperatorRouteEventMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OperatorRouteEventMessage> operatorRouteEventListenerContainerFactory(
            ConsumerFactory<String, OperatorRouteEventMessage> operatorRouteEventConsumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, OperatorRouteEventMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(operatorRouteEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, OperatorOperatorEventMessage> operatorOperatorEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(), typedJsonDeserializer(OperatorOperatorEventMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OperatorOperatorEventMessage> operatorOperatorEventListenerContainerFactory(
            ConsumerFactory<String, OperatorOperatorEventMessage> operatorOperatorEventConsumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, OperatorOperatorEventMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(operatorOperatorEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> catalogEventProducerFactory(KafkaProperties kafkaProperties,
                                                                         KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducerBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> catalogEventProducerFactory) {
        return new KafkaTemplate<>(catalogEventProducerFactory);
    }

    private Map<String, Object> consumerProperties(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumerBootstrapServers());
        return properties;
    }

    private <T> ErrorHandlingDeserializer<T> typedJsonDeserializer(Class<T> targetType, ObjectMapper objectMapper) {
        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(targetType, objectMapper, false);
        jsonDeserializer.addTrustedPackages(targetType.getPackageName());
        return new ErrorHandlingDeserializer<>(jsonDeserializer);
    }
}
