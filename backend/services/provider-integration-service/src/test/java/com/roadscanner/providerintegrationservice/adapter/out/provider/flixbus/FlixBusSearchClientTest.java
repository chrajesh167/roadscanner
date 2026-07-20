package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Uses {@link MockRestServiceServer} to stub FlixBus's response without a real server — proving
 * the request shape (query params, Authorization header) and response mapping documented in
 * {@link FlixBusMapper}'s Javadoc, without depending on real FlixBus access this project doesn't
 * have (see {@link FlixBusProperties}'s Javadoc). */
class FlixBusSearchClientTest {

    private static final String BASE_URL = "http://flixbus.test";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ProviderSession SESSION = ProviderSession.open(ProviderSessionId.generate(),
            ProviderType.FLIXBUS, new ProviderToken("token-123", null, "Bearer", Instant.parse("2026-08-01T00:00:00Z")),
            Instant.parse("2026-07-01T00:00:00Z"));

    private MockRestServiceServer mockServer;
    private FlixBusSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new FlixBusSearchClient(builder.build(), new FlixBusMapper(CLOCK), new FlixBusExceptionTranslator(CLOCK));
    }

    @Test
    void mapsASuccessfulResponseToProviderTrips() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/v1/trips?origin={origin}&destination={destination}&date={date}",
                        "Mumbai", "Pune", "2026-08-01"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andRespond(withSuccess("""
                        {"trips": [{"tripId": "FB-1", "operator": "FlixBus", "origin": "Mumbai", "destination": "Pune",
                        "departureTimeUtc": "2026-08-01T08:00:00Z", "arrivalTimeUtc": "2026-08-01T12:00:00Z",
                        "busType": "AC Sleeper", "fare": {"amount": 500.00, "currency": "INR"}, "seatsAvailable": 10}]}
                        """, MediaType.APPLICATION_JSON));

        List<ProviderTrip> trips = client.search(SESSION, new SearchCriteria("Mumbai", "Pune", LocalDate.of(2026, 8, 1)));

        assertThat(trips).hasSize(1);
        assertThat(trips.get(0).providerTripId()).isEqualTo("FB-1");
        assertThat(trips.get(0).providerType()).isEqualTo(ProviderType.FLIXBUS);
        mockServer.verify();
    }

    @Test
    void translatesAServerErrorIntoProviderUnavailable() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/v1/trips?origin={origin}&destination={destination}&date={date}",
                        "Mumbai", "Pune", "2026-08-01"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search(SESSION, new SearchCriteria("Mumbai", "Pune", LocalDate.of(2026, 8, 1))))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
