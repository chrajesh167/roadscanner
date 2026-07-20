package com.roadscanner.providerintegrationservice.adapter.out.kafka;

import com.roadscanner.providerintegrationservice.domain.model.AuditEventType;
import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditPublisher;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.KafkaContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Publishes a real message to a real Kafka (Testcontainers) and reads it back with a plain
 * consumer — proving the JSON shape and topic/key routing actually work, matching
 * {@code search-service}'s {@code KafkaEventProcessingIntegrationTest} rationale (there for
 * consumption, here for production — the two halves of this service's Kafka surface). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class KafkaAuditPublisherAdapterTest {

    @Autowired
    private AuditPublisher auditPublisher;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${roadscanner.provider.kafka.audit-topic}")
    private String auditTopic;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(auditTopic));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void publishesTheAuditRecordAsJsonKeyedByProviderType() {
        AuditRecord record = AuditRecord.of(ProviderType.MOCK, AuditEventType.PROVIDER_UNAVAILABLE, null,
                "Provider MOCK transitioned to UNAVAILABLE", Instant.parse("2026-07-01T00:00:00Z"));

        auditPublisher.publish(record);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ConsumerRecord<String, String>> records = drain();
            assertThat(records).isNotEmpty();
            ConsumerRecord<String, String> received = records.get(records.size() - 1);
            assertThat(received.key()).isEqualTo("MOCK");
            assertThat(received.value()).contains("\"eventType\":\"PROVIDER_UNAVAILABLE\"")
                    .contains("\"providerType\":\"MOCK\"");
        });
    }

    private List<ConsumerRecord<String, String>> drain() {
        Iterable<ConsumerRecord<String, String>> records = org.springframework.kafka.test.utils.KafkaTestUtils
                .getRecords(consumer, java.time.Duration.ofSeconds(2)).records(auditTopic);
        List<ConsumerRecord<String, String>> result = new java.util.ArrayList<>();
        records.forEach(result::add);
        return result;
    }
}
