package com.roadscanner.providerintegrationservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer wiring for {@code KafkaAuditPublisherAdapter} — a single, explicitly-built
 * {@link ProducerFactory}/{@link KafkaTemplate} pair, matching {@code search-service}'s
 * {@code KafkaConfig} rationale for hand-building factories rather than relying purely on Spring
 * Boot's autoconfigured default: bootstrap servers are read from {@link KafkaConnectionDetails}
 * explicitly so Testcontainers' {@code @ServiceConnection} override (used in tests) is honored,
 * which a hand-built {@link ProducerFactory} bypasses unless applied here.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, Object> providerAuditProducerFactory(KafkaProperties kafkaProperties,
                                                                          KafkaConnectionDetails connectionDetails) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducerBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> providerAuditProducerFactory) {
        return new KafkaTemplate<>(providerAuditProducerFactory);
    }
}
