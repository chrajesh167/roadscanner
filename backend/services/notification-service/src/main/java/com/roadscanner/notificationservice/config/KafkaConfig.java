package com.roadscanner.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.notificationservice.adapter.in.kafka.BookingEventMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring: one explicit, type-bound consumer factory for {@code booking-events}, reading
 * {@link KafkaConnectionDetails} explicitly so Testcontainers' {@code @ServiceConnection} override
 * is honored in tests — the same shape {@code payment-service}'s {@code KafkaConfig} uses for the
 * same topic, and for the same reason: the producer emits no Spring-Kafka type headers, so the
 * target type must be bound here.
 *
 * <p>This service is a pure consumer. It publishes nothing, so there is no producer factory and no
 * {@code KafkaTemplate} — which also means no dead-letter topic: a
 * {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer} would need one.
 *
 * <p><strong>No retry.</strong> {@code FixedBackOff(0, 0)} is deliberate. Every failure the
 * listener can suffer is either permanent (a malformed event) or already recorded in the
 * notification log with its reason (a refused mail server) — and a redelivery would re-run a send
 * that may already have reached the customer. Retrying at this level trades a missing notification,
 * which is visible and recoverable, for a duplicate one, which is neither.
 *
 * <p>{@code missingTopicsFatal(false)}: startup is never gated on {@code booking-events} existing,
 * matching the posture the other services take toward topics they consume.
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(0L, 0L));
    }

    @Bean
    public ConsumerFactory<String, BookingEventMessage> bookingEventConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails, ObjectMapper objectMapper) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumerBootstrapServers());
        return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(),
                typedJsonDeserializer(objectMapper));
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

    private ErrorHandlingDeserializer<BookingEventMessage> typedJsonDeserializer(ObjectMapper objectMapper) {
        JsonDeserializer<BookingEventMessage> jsonDeserializer =
                new JsonDeserializer<>(BookingEventMessage.class, objectMapper, false);
        jsonDeserializer.addTrustedPackages(BookingEventMessage.class.getPackageName());
        return new ErrorHandlingDeserializer<>(jsonDeserializer);
    }
}
