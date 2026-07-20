package com.roadscanner.providerintegrationservice;

import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full HTTP-surface flow against the Mock provider, over real Postgres/Redis/Kafka
 * (Testcontainers) — the provider-integration-service equivalent of {@code auth-service}'s
 * {@code AuthServiceEndToEndTest} / {@code search-service}'s {@code SearchServiceEndToEndTest}.
 * Proves the entire chain — REST → use case → {@code ProviderClientRegistry} →
 * {@code MockProviderClientAdapter} → persistence/cache — works together end to end, not just
 * each layer in isolation.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProviderIntegrationServiceEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private String basePath(String suffix) {
        return "/internal/api/v1/providers/MOCK" + suffix;
    }

    private String authenticate() {
        ResponseEntity<Map> response = rest.postForEntity(basePath("/sessions"), null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("sessionId");
    }

    @Test
    void fullMockProviderJourneyFromAuthenticationToTicketDownload() {
        String sessionId = authenticate();

        ResponseEntity<Map> searchResponse = rest.getForEntity(
                basePath("/sessions/" + sessionId + "/trips?origin=Mumbai&destination=Pune&date=2026-08-01"), Map.class);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> trips = (List<Map<String, Object>>) searchResponse.getBody().get("trips");
        assertThat(trips).isNotEmpty();
        String providerTripId = (String) trips.get(0).get("providerTripId");

        ResponseEntity<Map> seatMapResponse = rest.getForEntity(
                basePath("/sessions/" + sessionId + "/trips/" + providerTripId + "/seat-map"), Map.class);
        assertThat(seatMapResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> seats = (List<Map<String, Object>>) seatMapResponse.getBody().get("seats");
        String availableSeat = seats.stream().filter(s -> "AVAILABLE".equals(s.get("status")))
                .map(s -> (String) s.get("seatNumber")).findFirst().orElseThrow();

        ResponseEntity<Map> blockResponse = rest.postForEntity(
                basePath("/sessions/" + sessionId + "/trips/" + providerTripId + "/seat-blocks"),
                Map.of("seatNumbers", List.of(availableSeat)), Map.class);
        assertThat(blockResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blockReference = (String) blockResponse.getBody().get("providerBlockReference");

        Map<String, Object> passenger = Map.of("fullName", "Jane Doe", "age", 30, "gender", "F", "seatNumber", availableSeat);
        ResponseEntity<Map> confirmResponse = rest.postForEntity(
                basePath("/sessions/" + sessionId + "/seat-blocks/" + blockReference + "/booking"),
                Map.of("providerTripId", providerTripId, "passengers", List.of(passenger)), Map.class);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String bookingReference = (String) confirmResponse.getBody().get("bookingReference");
        assertThat(bookingReference).isNotBlank();

        ResponseEntity<Map> ticketResponse = rest.getForEntity(
                basePath("/sessions/" + sessionId + "/bookings/" + bookingReference + "/ticket"), Map.class);
        assertThat(ticketResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) ticketResponse.getBody().get("contentBase64")).isNotBlank();
    }

    @Test
    void releaseSeatIsIdempotent() {
        String sessionId = authenticate();
        ResponseEntity<Map> searchResponse = rest.getForEntity(
                basePath("/sessions/" + sessionId + "/trips?origin=Chennai&destination=Bengaluru&date=2026-08-02"), Map.class);
        String providerTripId = ((List<Map<String, Object>>) searchResponse.getBody().get("trips")).get(0)
                .get("providerTripId").toString();
        ResponseEntity<Map> seatMapResponse = rest.getForEntity(
                basePath("/sessions/" + sessionId + "/trips/" + providerTripId + "/seat-map"), Map.class);
        String availableSeat = ((List<Map<String, Object>>) seatMapResponse.getBody().get("seats")).stream()
                .filter(s -> "AVAILABLE".equals(s.get("status"))).map(s -> (String) s.get("seatNumber")).findFirst()
                .orElseThrow();
        ResponseEntity<Map> blockResponse = rest.postForEntity(
                basePath("/sessions/" + sessionId + "/trips/" + providerTripId + "/seat-blocks"),
                Map.of("seatNumbers", List.of(availableSeat)), Map.class);
        String blockReference = (String) blockResponse.getBody().get("providerBlockReference");

        ResponseEntity<Map> firstRelease = rest.exchange(
                basePath("/sessions/" + sessionId + "/seat-blocks/" + blockReference), HttpMethod.DELETE,
                HttpEntity.EMPTY, Map.class);
        ResponseEntity<Map> secondRelease = rest.exchange(
                basePath("/sessions/" + sessionId + "/seat-blocks/" + blockReference), HttpMethod.DELETE,
                HttpEntity.EMPTY, Map.class);

        assertThat(firstRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void capabilitiesAndHealthEndpointsWork() {
        ResponseEntity<Map> capabilities = rest.getForEntity(basePath("/capabilities"), Map.class);
        assertThat(capabilities.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<String>) capabilities.getBody().get("capabilities")).contains("SEARCH", "SEAT_MAP");

        ResponseEntity<Map> health = rest.getForEntity(basePath("/health"), Map.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().get("currentState")).isEqualTo("HEALTHY");
    }

    @Test
    void authenticatingAnUnsupportedProviderReturns404() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/internal/api/v1/providers/REDBUS/sessions", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
