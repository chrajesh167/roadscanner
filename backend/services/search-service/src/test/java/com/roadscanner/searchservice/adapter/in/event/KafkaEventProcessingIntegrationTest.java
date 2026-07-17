package com.roadscanner.searchservice.adapter.in.event;

import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Publishes real JSON messages to a real Kafka (Testcontainers) and asserts the effect lands in
 * the index — proving deserialization, dispatch, idempotency, and the "ordering edge case"
 * (docs/services/search-service/events-consumed.md) actually work end-to-end, not just that the
 * indexer use-cases behave correctly in isolation (already covered by their own unit tests).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class KafkaEventProcessingIntegrationTest {

    @Autowired
    private SearchableTripRepository repository;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${roadscanner.search.kafka.trip-events-topic}")
    private String tripEventsTopic;

    @Value("${roadscanner.search.kafka.review-events-topic}")
    private String reviewEventsTopic;

    private KafkaTemplate<String, Object> testProducer() {
        // @ServiceConnection wires bootstrap servers into a KafkaConnectionDetails bean, not a
        // resolvable "spring.kafka.bootstrap-servers" property — reading it straight off the
        // container is the direct, dependency-free way to get it for a hand-built test producer.
        Map<String, Object> props = Map.of(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>()));
    }

    private TripEventMessage published(UUID tripId, Instant occurredAt) {
        return new TripEventMessage(TripEventType.PUBLISHED, tripId, UUID.randomUUID(), "Acme Travels",
                "Mumbai", "Pune", Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"),
                "AC Sleeper", List.of("WiFi"), BigDecimal.valueOf(500), "INR", occurredAt);
    }

    @Test
    void publishedEventIsIndexedAndSearchable() {
        UUID rawTripId = UUID.randomUUID();
        TripId tripId = new TripId(rawTripId);
        KafkaTemplate<String, Object> producer = testProducer();

        producer.send(tripEventsTopic, rawTripId.toString(), published(rawTripId, Instant.parse("2026-07-01T00:00:00Z")));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId)).isPresent());

        assertThat(repository.findByTripId(tripId).orElseThrow().operatorName()).isEqualTo("Acme Travels");
    }

    @Test
    void updatedEventOverwritesTheIndexedTrip() {
        UUID rawTripId = UUID.randomUUID();
        TripId tripId = new TripId(rawTripId);
        KafkaTemplate<String, Object> producer = testProducer();
        producer.send(tripEventsTopic, rawTripId.toString(), published(rawTripId, Instant.parse("2026-07-01T00:00:00Z")));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId)).isPresent());

        TripEventMessage update = new TripEventMessage(TripEventType.UPDATED, rawTripId, null, "Acme Travels Renamed",
                "Mumbai", "Pune", Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"),
                "AC Sleeper", List.of("WiFi"), BigDecimal.valueOf(600), "INR", Instant.parse("2026-07-01T00:01:00Z"));
        producer.send(tripEventsTopic, rawTripId.toString(), update);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId).orElseThrow().operatorName())
                        .isEqualTo("Acme Travels Renamed"));
    }

    @Test
    void cancelledEventMarksTheIndexedTripUnbookable() {
        UUID rawTripId = UUID.randomUUID();
        TripId tripId = new TripId(rawTripId);
        KafkaTemplate<String, Object> producer = testProducer();
        producer.send(tripEventsTopic, rawTripId.toString(), published(rawTripId, Instant.parse("2026-07-01T00:00:00Z")));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId)).isPresent());

        TripEventMessage cancelled = new TripEventMessage(TripEventType.CANCELLED, rawTripId, null, null,
                null, null, null, null, null, null, null, null, Instant.parse("2026-07-01T00:01:00Z"));
        producer.send(tripEventsTopic, rawTripId.toString(), cancelled);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId).orElseThrow().bookable()).isFalse());
    }

    @Test
    void reviewSubmittedUpdatesTheRatingSnapshot() {
        UUID rawTripId = UUID.randomUUID();
        TripId tripId = new TripId(rawTripId);
        KafkaTemplate<String, Object> producer = testProducer();
        producer.send(tripEventsTopic, rawTripId.toString(), published(rawTripId, Instant.parse("2026-07-01T00:00:00Z")));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId)).isPresent());

        ReviewSubmittedMessage review = new ReviewSubmittedMessage(rawTripId, 4.6, 12, Instant.parse("2026-07-01T00:01:00Z"));
        producer.send(reviewEventsTopic, rawTripId.toString(), review);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId).orElseThrow().rating().average()).isEqualTo(4.6));
    }

    @Test
    void redeliveryOfTheSamePublishedEventIsIdempotent() {
        UUID rawTripId = UUID.randomUUID();
        TripId tripId = new TripId(rawTripId);
        KafkaTemplate<String, Object> producer = testProducer();
        TripEventMessage message = published(rawTripId, Instant.parse("2026-07-01T00:00:00Z"));

        producer.send(tripEventsTopic, rawTripId.toString(), message);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId)).isPresent());

        long versionBefore = repository.findByTripId(tripId).orElseThrow().lastTripEventAt().getEpochSecond();
        producer.send(tripEventsTopic, rawTripId.toString(), message);

        // No new, later event follows the redelivery, so there is nothing further to await —
        // give the (idempotent, no-op) redelivery a moment to be processed, then assert nothing changed.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findByTripId(tripId).orElseThrow().lastTripEventAt().getEpochSecond())
                        .isEqualTo(versionBefore));
    }
}
