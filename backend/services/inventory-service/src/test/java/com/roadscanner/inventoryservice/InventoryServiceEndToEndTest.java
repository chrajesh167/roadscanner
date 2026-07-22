package com.roadscanner.inventoryservice;

import com.roadscanner.inventoryservice.adapter.in.event.OperatorTripEventMessage;
import com.roadscanner.inventoryservice.adapter.in.event.OperatorTripEventType;
import com.roadscanner.inventoryservice.adapter.in.event.SeatEntryMessage;
import com.roadscanner.inventoryservice.testsupport.TestcontainersConfiguration;
import org.apache.kafka.clients.producer.ProducerConfig;
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
 * Full HTTP-surface flow against real Postgres and Kafka (Testcontainers) — the
 * inventory-service equivalent of {@code search-service}'s {@code SearchServiceEndToEndTest}.
 * Proves the Kafka-driven ingestion of {@code operator-service}'s trip events and the REST
 * catalog-query surface work together, not just each layer in isolation. No Redis container is
 * used — this service has none (see {@code TestcontainersConfiguration}'s Javadoc).
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InventoryServiceEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${roadscanner.inventory.kafka.operator-trip-events-topic}")
    private String tripEventsTopic;

    private KafkaTemplate<String, Object> testProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>()));
    }

    private UUID publishTrip(String origin, String destination) {
        UUID tripId = UUID.randomUUID();
        OperatorTripEventMessage message = new OperatorTripEventMessage(OperatorTripEventType.PUBLISHED, tripId,
                UUID.randomUUID(), "Acme Travels", origin, destination,
                Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"), "AC Sleeper",
                List.of("WiFi"), BigDecimal.valueOf(500), "INR",
                List.of(new SeatEntryMessage("L1", "LOWER", "SLEEPER", false, 1),
                        new SeatEntryMessage("L2", "LOWER", "SLEEPER", false, 2)),
                Instant.now());
        testProducer().send(tripEventsTopic, tripId.toString(), message);
        return tripId;
    }

    @Test
    void citiesEndpointReturnsSeedGeography() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/inventory/cities?q=Mum", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> cities = (List<Map<String, Object>>) response.getBody().get("cities");
        assertThat(cities).extracting(c -> c.get("name")).contains("Mumbai");
    }

    @Test
    void publishedTripBecomesQueryableViaMetadataAndSeatLayout() {
        String origin = "Mumbai-" + UUID.randomUUID();
        String destination = "Pune-" + UUID.randomUUID();
        UUID tripId = publishTrip(origin, destination);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<Map> response = rest.getForEntity("/api/v1/inventory/trips/{tripId}", Map.class, tripId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("origin")).isEqualTo(origin);
            assertThat(response.getBody().get("destination")).isEqualTo(destination);
            assertThat(response.getBody().get("bookable")).isEqualTo(true);
        });

        ResponseEntity<Map> seatLayoutResponse = rest.getForEntity(
                "/api/v1/inventory/trips/{tripId}/seat-layout", Map.class, tripId);
        assertThat(seatLayoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> seats = (List<Map<String, Object>>) seatLayoutResponse.getBody().get("seats");
        assertThat(seats).hasSize(2);
    }

    @Test
    void metadataLookupForAnUnknownTripReturns404() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/inventory/trips/{tripId}", Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void availabilityDegradesTo503ForAFirstPartyTripWithNoProviderMapping() {
        UUID tripId = publishTrip("Chennai-" + UUID.randomUUID(), "Bengaluru-" + UUID.randomUUID());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rest.getForEntity("/api/v1/inventory/trips/{tripId}", Map.class, tripId).getStatusCode())
                        .isEqualTo(HttpStatus.OK));

        ResponseEntity<Map> availability = rest.getForEntity(
                "/api/v1/inventory/trips/{tripId}/availability", Map.class, tripId);
        assertThat(availability.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void healthAndOpenApiAreServed() {
        assertThat(rest.getForEntity("/actuator/health", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> apiDocs = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocs.getBody())
                .contains("/api/v1/inventory/trips/{tripId}/availability")
                .contains("/api/v1/inventory/cities");
    }
}
