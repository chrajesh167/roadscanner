package com.roadscanner.notificationservice.testsupport;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * A producer that exists only in tests, standing in for booking-service.
 *
 * <p>Deliberately not in the main source set: this service publishes nothing, and adding a
 * {@code KafkaTemplate} to production wiring so a test could use it would misrepresent it as a
 * producer to anyone reading the configuration.
 *
 * <p>Type headers are switched off so the payload on the wire looks exactly like booking-service's
 * — a plain JSON object with no Spring-Kafka class hints. With them enabled the consumer would be
 * told which class to build, and the test would stop proving that the real message deserializes.
 */
@TestConfiguration
public class TestKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> testProducerFactory(KafkaProperties kafkaProperties,
                                                                KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducerBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> testKafkaTemplate(ProducerFactory<String, Object> testProducerFactory) {
        return new KafkaTemplate<>(testProducerFactory);
    }
}
