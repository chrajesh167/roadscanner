package com.roadscanner.searchservice;

import com.roadscanner.searchservice.adapter.in.event.TripEventMessage;
import com.roadscanner.searchservice.adapter.in.event.TripEventType;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Full HTTP-surface flows against real Postgres, Redis, and Kafka (Testcontainers) — the
 * search-service equivalent of {@code auth-service}'s {@code AuthServiceEndToEndTest}. Proves
 * the Kafka-driven index and the REST query surface work together, not just each in isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SearchServiceEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${roadscanner.search.kafka.trip-events-topic}")
    private String tripEventsTopic;

    private KafkaTemplate<String, Object> testProducer() {
        Map<String, Object> props = Map.of(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>()));
    }

    private UUID publishTrip(String origin, String destination, BigDecimal fare) {
        UUID tripId = UUID.randomUUID();
        TripEventMessage message = new TripEventMessage(TripEventType.PUBLISHED, tripId, UUID.randomUUID(),
                "Acme Travels", origin, destination,
                Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"),
                "AC Sleeper", List.of("WiFi"), fare, "INR", Instant.now());
        testProducer().send(tripEventsTopic, tripId.toString(), message);
        return tripId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void publishedTripBecomesSearchableAndReturnsWithUnknownAvailability() {
        String origin = "Mumbai-" + UUID.randomUUID();
        String destination = "Pune-" + UUID.randomUUID();
        UUID tripId = publishTrip(origin, destination, BigDecimal.valueOf(500));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<Map> response = rest.getForEntity(
                    "/api/v1/search/trips?origin={o}&destination={d}&date=2026-08-01", Map.class, origin, destination);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
            assertThat(content).hasSize(1);
            assertThat(content.getFirst().get("tripId")).isEqualTo(tripId.toString());
            // inventory-service isn't running in this test environment (base-url points nowhere
            // reachable) — the "degrade, not fail" rule means the search still succeeds, with
            // availability marked unknown rather than the whole request failing.
            assertThat(content.getFirst().get("availabilityKnown")).isEqualTo(false);
        });
    }

    @Test
    void tripDetailIsReachableById() {
        String origin = "Delhi-" + UUID.randomUUID();
        String destination = "Agra-" + UUID.randomUUID();
        UUID tripId = publishTrip(origin, destination, BigDecimal.valueOf(300));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<Map> response = rest.getForEntity("/api/v1/search/trips/{tripId}", Map.class, tripId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("origin")).isEqualTo(origin);
        });
    }

    @Test
    void detailLookupForAnUnindexedTripReturnsNotFound() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/search/trips/{tripId}", Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void searchWithNoMatchesReturnsAnEmptyPageNotAnError() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/search/trips?origin={o}&destination={d}&date=2026-08-01", Map.class,
                "NoSuchOrigin-" + UUID.randomUUID(), "NoSuchDestination-" + UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) body(response).get("content")).isEmpty();
    }

    @Test
    void suggestionsReflectIndexedPlaceNames() {
        String origin = "Bengaluru-" + UUID.randomUUID();
        String destination = "Chennai-" + UUID.randomUUID();
        publishTrip(origin, destination, BigDecimal.valueOf(400));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<Map> response = rest.getForEntity(
                    "/api/v1/search/suggestions?query={q}", Map.class, origin.substring(0, 9));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            List<String> suggestions = (List<String>) body(response).get("suggestions");
            assertThat(suggestions).contains(origin);
        });
    }

    @Test
    void reindexTruncatesTheIndexAndKafkaReplayRepopulatesIt() {
        String origin = "Jaipur-" + UUID.randomUUID();
        String destination = "Udaipur-" + UUID.randomUUID();
        UUID tripId = publishTrip(origin, destination, BigDecimal.valueOf(350));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<Map> response = rest.getForEntity("/api/v1/search/trips/{tripId}", Map.class, tripId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        });

        ResponseEntity<Void> reindexResponse = rest.postForEntity("/internal/search/reindex", null, Void.class);
        assertThat(reindexResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // The trip disappears immediately (index truncated)...
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rest.getForEntity("/api/v1/search/trips/{tripId}", Map.class, tripId).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        // ...then reappears once Kafka redelivers the retained TripPublished event from the
        // beginning — proving the replay trigger genuinely reprocesses history, not just clears state.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(rest.getForEntity("/api/v1/search/trips/{tripId}", Map.class, tripId).getStatusCode())
                        .isEqualTo(HttpStatus.OK));
    }

    @Test
    void healthAndOpenApiAreServed() {
        assertThat(rest.getForEntity("/actuator/health", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> apiDocs = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocs.getBody())
                .contains("/api/v1/search/trips")
                .contains("/api/v1/search/suggestions");
    }
}
