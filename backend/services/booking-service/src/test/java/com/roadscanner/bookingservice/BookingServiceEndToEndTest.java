package com.roadscanner.bookingservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roadscanner.bookingservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.testsupport.TestcontainersConfiguration;
import com.roadscanner.bookingservice.testsupport.security.TestJwtIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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

    /**
     * The stub servers are static, so without this every test's registrations would survive into
     * the next one. Two tests stubbing the same path then differ only by which happened to register
     * last — a dependency on execution order that is invisible until it breaks. Each test declares
     * the stubs it needs and starts from nothing.
     */
    @BeforeEach
    void resetStubs() {
        INVENTORY_SERVICE.resetAll();
        PROVIDER_INTEGRATION_SERVICE.resetAll();
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

    /**
     * Stubs the provider session and seat-block calls, issuing a <strong>fresh block reference on
     * every invocation</strong>.
     *
     * <p>It has to be fresh. {@code provider_block_reference} is {@code UNIQUE} on both
     * {@code seat_holds} and {@code bookings} (V1__create_booking_tables.sql), and the schema is
     * right to enforce that — one provider-side reservation must never back two local records.
     * A constant here meant the second test in the class to reach {@code POST /api/v1/bookings}
     * collided on that constraint, so its booking was never created and the rest of the test
     * asserted against a null id. The database was behaving correctly; the fixture was not.
     *
     * @return the block reference this stub will hand back, so a caller can assert on it
     */
    private static final Map<String, Object> PASSENGER_BODY = Map.of(
            "firstName", "Asha", "lastName", "Menon", "birthDate", "1994-03-17",
            "gender", "female", "seatNumber", "L1");

    private static final Map<String, Object> CONTACT_BODY = Map.of(
            "phone", "+919876543210", "email", "asha@example.com", "communicationPreference", "email");

    private String stubProviderAuthenticateAndBlock() {
        String blockReference = "block-ref-" + UUID.randomUUID();

        PROVIDER_INTEGRATION_SERVICE.stubFor(post(urlPathMatching("/internal/api/v1/providers/MOCK/sessions"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"sessionId":"%s","providerType":"MOCK","expiresAt":"2099-01-01T00:00:00Z"}
                        """.formatted(UUID.randomUUID()))));
        PROVIDER_INTEGRATION_SERVICE.stubFor(post(urlPathMatching(
                        "/internal/api/v1/providers/MOCK/sessions/[^/]+/trips/MOCK-TRIP-1/seat-blocks"))
                // Asserted, not ignored: provider-integration-service requires the occupant of each
                // seat, and a stub that accepted any body is exactly why the old mismatch shipped.
                .withRequestBody(matchingJsonPath("$.passengers[0].firstName", equalTo("Asha")))
                .withRequestBody(matchingJsonPath("$.passengers[0].lastName", equalTo("Menon")))
                .withRequestBody(matchingJsonPath("$.passengers[0].birthDate", equalTo("1994-03-17")))
                .withRequestBody(matchingJsonPath("$.passengers[0].seatNumber", equalTo("L1")))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"reservationId":"%s","providerBlockReference":"%s","providerTripId":"MOCK-TRIP-1",
                         "seatNumbers":["L1"],"status":"BLOCKED","blockedAt":"2026-08-01T00:00:00Z",
                         "expiresAt":"2099-01-01T00:00:00Z"}
                        """.formatted(UUID.randomUUID(), blockReference))));

        return blockReference;
    }

    @Test
    void holdSeatsThenCreateBookingSucceedsAndBookingIsRetrievable() {
        UUID tripId = UUID.randomUUID();
        UUID travelerId = UUID.randomUUID();
        stubBookableTrip(tripId, "PROVIDER_SYNCED", true);
        stubProviderAuthenticateAndBlock();

        ResponseEntity<Map> holdResponse = rest.exchange("/api/v1/bookings/holds", HttpMethod.POST,
                new HttpEntity<>(Map.of("tripId", tripId.toString(), "passengers", List.of(PASSENGER_BODY)),
                        authHeaders(travelerId)),
                Map.class);
        assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String seatHoldId = (String) holdResponse.getBody().get("seatHoldId");

        ResponseEntity<Map> createResponse = rest.exchange("/api/v1/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("seatHoldId", seatHoldId, "contact", CONTACT_BODY),
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
                new HttpEntity<>(Map.of("tripId", tripId.toString(), "passengers", List.of(PASSENGER_BODY)),
                        authHeaders(travelerId)),
                Map.class);
        assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String seatHoldId = (String) holdResponse.getBody().get("seatHoldId");

        ResponseEntity<Map> createResponse = rest.exchange("/api/v1/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("seatHoldId", seatHoldId, "contact", CONTACT_BODY),
                        authHeaders(travelerId)),
                Map.class);
        // Asserted before the request under test runs. Without it a failed setup leaves bookingId
        // null, the URL below becomes /api/v1/bookings/null, and the 400 that comes back is a
        // parsing error being read as an authorization result — which is exactly how this test
        // failed while appearing to be about visibility.
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = (String) createResponse.getBody().get("bookingId");
        assertThat(bookingId).isNotNull();

        ResponseEntity<Map> forbidden = rest.exchange("/api/v1/bookings/" + bookingId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(UUID.randomUUID())), Map.class);

        // 404, not 403: a booking the caller doesn't own must not be distinguishable from one that
        // doesn't exist (docs/services/booking-service/api-summary.md).
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
                new HttpEntity<>(Map.of("tripId", tripId.toString(), "passengers", List.of(PASSENGER_BODY)),
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
