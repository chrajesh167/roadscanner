package com.roadscanner.bookingservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roadscanner.bookingservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.testsupport.TestcontainersConfiguration;
import com.roadscanner.bookingservice.testsupport.security.TestJwtIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full HTTP-surface flow against real Postgres and Kafka (Testcontainers), with
 * {@code inventory-service} and {@code provider-integration-service} stubbed via an embedded
 * WireMock server — the {@code booking-service} equivalent of {@code inventory-service}'s and
 * {@code provider-integration-service}'s own end-to-end tests. A real HTTP stub (rather than
 * {@code MockRestServiceServer}) is used deliberately: {@code MockRestServiceServer} can only
 * bind to a {@code RestClient.Builder} before construction, not to the already-built
 * {@code RestClient} beans {@code config.RestClientConfig} exposes.
 *
 * <p>Drives: hold seats → create booking → get booking → ticket-not-yet-available, proving the
 * full REST + persistence + security chain works together. The payment→confirmation half of the
 * flow is covered at the application layer by {@code HandlePaymentCompletedServiceTest}, since
 * {@code payment-service} doesn't exist to drive it end-to-end here
 * (docs/services/booking-service/boundaries.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BookingServiceEndToEndTest {

    private static final WireMockServer INVENTORY_SERVICE = new WireMockServer(0);
    private static final WireMockServer PROVIDER_INTEGRATION_SERVICE = new WireMockServer(0);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private EphemeralJwtKeyPair ephemeralJwtKeyPair;

    @Value("${roadscanner.security.jwt.issuer}")
    private String issuer;

    @BeforeAll
    static void startStubs() {
        INVENTORY_SERVICE.start();
        PROVIDER_INTEGRATION_SERVICE.start();
    }

    @AfterAll
    static void stopStubs() {
        INVENTORY_SERVICE.stop();
        PROVIDER_INTEGRATION_SERVICE.stop();
    }

    @DynamicPropertySource
    static void stubBaseUrls(DynamicPropertyRegistry registry) {
        registry.add("roadscanner.booking.inventory-service.base-url", INVENTORY_SERVICE::baseUrl);
        registry.add("roadscanner.booking.provider-integration-service.base-url",
                PROVIDER_INTEGRATION_SERVICE::baseUrl);
    }

    private TestJwtIssuer jwtIssuer() {
        return new TestJwtIssuer(ephemeralJwtKeyPair, issuer);
    }

    private HttpHeaders authHeaders(UUID travelerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtIssuer().issue(travelerId, Role.TRAVELER));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void stubBookableTrip(UUID tripId, String supplyOrigin, boolean hasProviderMapping) {
        INVENTORY_SERVICE.stubFor(get(urlPathMatching("/api/v1/inventory/trips/" + tripId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"tripId":"%s","origin":"Mumbai","destination":"Pune",
                         "departureTime":"2026-08-01T08:00:00Z","arrivalTime":"2026-08-01T12:00:00Z",
                         "operatorId":null,"operatorDisplayName":"Acme Travels","busTypeCategory":"AC Sleeper",
                         "amenities":["WiFi"],"fareAmount":500,"fareCurrency":"INR","bookable":true,
                         "supplyOrigin":"%s"}
                        """.formatted(tripId, supplyOrigin))));
        if (hasProviderMapping) {
            INVENTORY_SERVICE.stubFor(get(urlPathMatching("/api/v1/inventory/trips/" + tripId + "/provider-mapping"))
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                            {"tripId":"%s","providerType":"MOCK","providerTripId":"MOCK-TRIP-1",
                             "lastSyncedAt":"2026-08-01T00:00:00Z","syncStatus":"SUCCESS"}
                            """.formatted(tripId))));
        } else {
            INVENTORY_SERVICE.stubFor(get(urlPathMatching("/api/v1/inventory/trips/" + tripId + "/provider-mapping"))
                    .willReturn(aResponse().withStatus(404)));
        }
    }

    private void stubProviderAuthenticateAndBlock() {
        PROVIDER_INTEGRATION_SERVICE.stubFor(post(urlPathMatching("/internal/api/v1/providers/MOCK/sessions"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"sessionId":"%s","providerType":"MOCK","expiresAt":"2099-01-01T00:00:00Z"}
                        """.formatted(UUID.randomUUID()))));
        PROVIDER_INTEGRATION_SERVICE.stubFor(post(urlPathMatching(
                        "/internal/api/v1/providers/MOCK/sessions/[^/]+/trips/MOCK-TRIP-1/seat-blocks"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"reservationId":"res-1","providerBlockReference":"block-ref-1","providerTripId":"MOCK-TRIP-1",
                         "seatNumbers":["L1"],"status":"BLOCKED","blockedAt":"2026-08-01T00:00:00Z",
                         "expiresAt":"2099-01-01T00:00:00Z"}
                        """)));
    }

    @Test
    void holdSeatsThenCreateBookingSucceedsAndBookingIsRetrievable() {
        UUID tripId = UUID.randomUUID();
        UUID travelerId = UUID.randomUUID();
        stubBookableTrip(tripId, "PROVIDER_SYNCED", true);
        stubProviderAuthenticateAndBlock();

        ResponseEntity<Map> holdResponse = rest.exchange("/api/v1/bookings/holds", HttpMethod.POST,
                new HttpEntity<>(Map.of("tripId", tripId.toString(), "seatNumbers", List.of("L1")),
                        authHeaders(travelerId)),
                Map.class);
        assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String seatHoldId = (String) holdResponse.getBody().get("seatHoldId");

        ResponseEntity<Map> createResponse = rest.exchange("/api/v1/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("seatHoldId", seatHoldId, "passengers",
                        List.of(Map.of("fullName", "Jane Doe", "age", 30, "gender", "F", "seatNumber", "L1"))),
                        authHeaders(travelerId)),
                Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().get("status")).isEqualTo("PENDING_PAYMENT");
        String bookingId = (String) createResponse.getBody().get("bookingId");

        ResponseEntity<Map> getResponse = rest.exchange("/api/v1/bookings/" + bookingId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(travelerId)), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("bookingId")).isEqualTo(bookingId);

        ResponseEntity<Map> ticketResponse = rest.exchange("/api/v1/bookings/" + bookingId + "/ticket",
                HttpMethod.GET, new HttpEntity<>(authHeaders(travelerId)), Map.class);
        assertThat(ticketResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherTravelersBookingIsNotVisible() {
        UUID tripId = UUID.randomUUID();
        UUID travelerId = UUID.randomUUID();
        stubBookableTrip(tripId, "PROVIDER_SYNCED", true);
        stubProviderAuthenticateAndBlock();

        ResponseEntity<Map> holdResponse = rest.exchange("/api/v1/bookings/holds", HttpMethod.POST,
                new HttpEntity<>(Map.of("tripId", tripId.toString(), "seatNumbers", List.of("L1")),
                        authHeaders(travelerId)),
                Map.class);
        String seatHoldId = (String) holdResponse.getBody().get("seatHoldId");
        ResponseEntity<Map> createResponse = rest.exchange("/api/v1/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("seatHoldId", seatHoldId, "passengers",
                        List.of(Map.of("fullName", "Jane Doe", "age", 30, "gender", "F", "seatNumber", "L1"))),
                        authHeaders(travelerId)),
                Map.class);
        String bookingId = (String) createResponse.getBody().get("bookingId");

        ResponseEntity<Map> forbidden = rest.exchange("/api/v1/bookings/" + bookingId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(UUID.randomUUID())), Map.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/bookings", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void holdingATripWithNoProviderMappingFails() {
        UUID tripId = UUID.randomUUID();
        stubBookableTrip(tripId, "FIRST_PARTY", false);

        ResponseEntity<Map> response = rest.exchange("/api/v1/bookings/holds", HttpMethod.POST,
                new HttpEntity<>(Map.of("tripId", tripId.toString(), "seatNumbers", List.of("L1")),
                        authHeaders(UUID.randomUUID())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void healthAndOpenApiAreServed() {
        assertThat(rest.getForEntity("/actuator/health", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> apiDocs = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocs.getBody()).contains("/api/v1/bookings");
    }
}
