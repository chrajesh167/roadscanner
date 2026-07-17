package com.roadscanner.searchservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.searchservice.adapter.in.event.ReviewSubmittedMessage;
import com.roadscanner.searchservice.adapter.in.event.TripEventMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Kafka consumer wiring for docs/services/search-service/events-consumed.md's two topics.
 *
 * Each topic gets its own {@link ConsumerFactory}/{@link ConcurrentKafkaListenerContainerFactory}
 * pair, bound to an explicit target type ({@link TripEventMessage}, {@link ReviewSubmittedMessage})
 * via a directly-constructed {@link JsonDeserializer} — not resolved from Spring's proprietary
 * type-id headers. This is a deliberate interoperability choice: {@code operator-service} and
 * {@code review-service} are different, independently built services
 * (docs/architecture/database-ownership.md — no service depends on another's internals), so this
 * consumer must not assume their producers emit a Spring-Kafka-specific header format.
 *
 * Both factories share one {@link DefaultErrorHandler}: a bounded number of retries, then
 * publication to a per-topic dead-letter topic (Kafka's default {@code <topic>.DLT} naming) via
 * {@link DeadLetterPublishingRecoverer} — the same "one mapping layer, not scattered try/catch"
 * philosophy {@code auth-service}'s {@code GlobalExceptionHandler} applies to HTTP, applied here
 * to this service's other inbound surface. A message that fails after retries (malformed
 * payload, a required field missing for its event type) is never silently dropped nor allowed to
 * block the partition indefinitely — it is quarantined for investigation.
 *
 * The value deserializer is wrapped in {@link ErrorHandlingDeserializer} specifically so a
 * poison message — one that fails at deserialization itself, before any listener method runs —
 * reaches the same {@link DefaultErrorHandler}/DLT path as a failure thrown from inside a
 * listener method, rather than a raw deserialization exception crashing the consumer thread.
 *
 * Bootstrap servers are read from {@link KafkaConnectionDetails} explicitly, not left to
 * {@code KafkaProperties.buildConsumerProperties()} alone — Spring Boot's autoconfigured Kafka
 * beans apply connection-details overrides (e.g. Testcontainers' {@code @ServiceConnection} in
 * tests) automatically, but a hand-built {@link ConsumerFactory} like these bypasses that unless
 * the override is applied here too.
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
    public ConsumerFactory<String, TripEventMessage> tripEventConsumerFactory(KafkaProperties kafkaProperties,
                                                                              KafkaConnectionDetails connectionDetails,
                                                                              ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(
                consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(),
                typedJsonDeserializer(TripEventMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TripEventMessage> tripEventListenerContainerFactory(
            ConsumerFactory<String, TripEventMessage> tripEventConsumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TripEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(tripEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, ReviewSubmittedMessage> reviewEventConsumerFactory(KafkaProperties kafkaProperties,
                                                                                      KafkaConnectionDetails connectionDetails,
                                                                                      ObjectMapper objectMapper) {
        return new DefaultKafkaConsumerFactory<>(
                consumerProperties(kafkaProperties, connectionDetails),
                new StringDeserializer(),
                typedJsonDeserializer(ReviewSubmittedMessage.class, objectMapper));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReviewSubmittedMessage> reviewEventListenerContainerFactory(
            ConsumerFactory<String, ReviewSubmittedMessage> reviewEventConsumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ReviewSubmittedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(reviewEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    private Map<String, Object> consumerProperties(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new java.util.HashMap<>(kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumerBootstrapServers());
        return properties;
    }

    private <T> ErrorHandlingDeserializer<T> typedJsonDeserializer(Class<T> targetType, ObjectMapper objectMapper) {
        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(targetType, objectMapper, false);
        jsonDeserializer.addTrustedPackages(targetType.getPackageName());
        return new ErrorHandlingDeserializer<>(jsonDeserializer);
    }
}
