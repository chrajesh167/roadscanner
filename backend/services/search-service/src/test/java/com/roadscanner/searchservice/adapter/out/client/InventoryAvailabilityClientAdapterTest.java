package com.roadscanner.searchservice.adapter.out.client;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.AvailabilityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Uses {@link MockRestServiceServer} — already available transitively via
 * spring-boot-starter-test, no extra dependency needed — to stub {@code inventory-service}'s
 * response without a real server, per the "degrade, not fail" rule
 * (docs/services/search-service/boundaries.md).
 */
class InventoryAvailabilityClientAdapterTest {

    private static final TripId TRIP_ID = new TripId(UUID.randomUUID());
    private static final String BASE_URL = "http://inventory-service.test";

    private MockRestServiceServer mockServer;
    private AvailabilityClient adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new InventoryAvailabilityClientAdapter(builder.build());
    }

    @Test
    void returnsKnownAvailabilityOnSuccess() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/inventory/trips/" + TRIP_ID.value() + "/availability"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("{\"availableSeats\": 17}", MediaType.APPLICATION_JSON));

        AvailabilityStatus status = adapter.fetchAvailability(TRIP_ID);

        assertThat(status).isEqualTo(AvailabilityStatus.of(17));
        mockServer.verify();
    }

    @Test
    void degradesToUnknownOnServerError() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/inventory/trips/" + TRIP_ID.value() + "/availability"))
                .andRespond(withServerError());

        AvailabilityStatus status = adapter.fetchAvailability(TRIP_ID);

        assertThat(status.isKnown()).isFalse();
    }

    @Test
    void degradesToUnknownOnMalformedBody() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/inventory/trips/" + TRIP_ID.value() + "/availability"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        AvailabilityStatus status = adapter.fetchAvailability(TRIP_ID);

        assertThat(status.isKnown()).isFalse();
    }

    @Test
    void degradesToUnknownOnConnectionFailure() {
        // Throwing an IOException from the ResponseCreator simulates a real transport failure
        // (connection refused, DNS failure) — RestClient wraps it as a ResourceAccessException,
        // a RestClientException subtype, exercising the same catch as an actual outage would.
        mockServer.expect(requestTo(BASE_URL + "/api/v1/inventory/trips/" + TRIP_ID.value() + "/availability"))
                .andRespond(request -> {
                    throw new java.io.IOException("Connection refused");
                });

        AvailabilityStatus status = adapter.fetchAvailability(TRIP_ID);

        assertThat(status.isKnown()).isFalse();
    }
}
