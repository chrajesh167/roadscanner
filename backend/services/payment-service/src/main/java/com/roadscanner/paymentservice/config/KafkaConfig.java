package com.roadscanner.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.paymentservice.adapter.in.event.BookingEventMessage;
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
 * Kafka wiring: one explicit, type-bound consumer factory for {@code booking-service}'s
 * {@code booking-events} (the {@code BookingCancelled} reconciliation — matching
 * {@code booking-service}'s own {@code KafkaConfig} rationale, since the upstream producer emits no
 * Spring-Kafka type headers), and one producer for this service's own two publish topics
 * ({@code payment-events}, {@code payment-lifecycle-events}), reading {@link KafkaConnectionDetails}
 * explicitly so Testcontainers' {@code @ServiceConnection} override is honored in tests.
 *
 * <p>{@code missingTopicsFatal(false)} on the listener container: this service's startup is never
 * gated on {@code booking-events} existing, the same explicit posture {@code booking-service} takes
 * toward the not-yet-real payment topic.
 */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
    }

    @Bean
    public ConsumerFactory<String, BookingEventMessage> bookingEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(), typedJsonDeserializer(objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingEventMessage> bookingEventListenerContainerFactory(
            ConsumerFactory<String, BookingEventMessage> bookingEventConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, BookingEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookingEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> paymentEventProducerFactory(KafkaProperties kafkaProperties,
                                                                        KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducerBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> paymentEventProducerFactory) {
        return new KafkaTemplate<>(paymentEventProducerFactory);
    }

    private Map<String, Object> consumerProperties(KafkaProperties kafkaProperties,
                                                   KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumerBootstrapServers());
        return properties;
    }

    private ErrorHandlingDeserializer<BookingEventMessage> typedJsonDeserializer(ObjectMapper objectMapper) {
        JsonDeserializer<BookingEventMessage> jsonDeserializer =
                new JsonDeserializer<>(BookingEventMessage.class, objectMapper, false);
        jsonDeserializer.addTrustedPackages(BookingEventMessage.class.getPackageName());
        return new ErrorHandlingDeserializer<>(jsonDeserializer);
    }
}
