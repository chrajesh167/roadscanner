package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderAuthenticationException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Uses {@link MockRestServiceServer} to stub FlixBus's response without a real server — proving the
 * request shape (query params, authentication header) and response mapping documented in
 * {@link FlixBusMapper}'s Javadoc, without depending on real FlixBus access this project does not
 * have (see {@link FlixBusProperties}'s Javadoc).
 */
class FlixBusSearchClientTest {

    private static final String BASE_URL = "http://flixbus.test";
    private static final String TRIPS_URI = BASE_URL + "/v1/trips?origin={origin}&destination={destination}&date={date}";
    private static final Instant FIXED = Instant.parse("2026-07-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);
    private static final String PARTNER_TOKEN = "partner-token-abc";

    private static final Provider PROVIDER = Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS,
            ProviderCategory.BUS, "FlixBus", true, Set.of(), "https://partner.test", 5_000, 2, FIXED, FIXED);

    private static final SearchCriteria CRITERIA = new SearchCriteria("Mumbai", "Pune", LocalDate.of(2026, 8, 1));

    private MockRestServiceServer mockServer;
    private RestClient.Builder builder;
    private FlixBusSearchClient client;

    /** The encrypted store the adapter reads its partner token from — never configuration. */
    private static InMemoryProviderCredentialsRepository storeHolding(String partnerToken) {
        InMemoryProviderCredentialsRepository repository = new InMemoryProviderCredentialsRepository();
        repository.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), PROVIDER.id(),
                "partner@roadscanner.com", "s3cret", partnerToken, FIXED));
        return repository;
    }

    private FlixBusSearchClient clientBackedBy(InMemoryProviderCredentialsRepository credentials) {
        return new FlixBusSearchClient(builder.build(), new FlixBusMapper(CLOCK),
                new FlixBusExceptionTranslator(CLOCK), new FlixBusCredentials(credentials));
    }

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = clientBackedBy(storeHolding(PARTNER_TOKEN));
    }

    private static String tripsBody() {
        return """
                {"trips": [{"tripId": "FB-1", "operator": "FlixBus", "origin": "Mumbai", "destination": "Pune",
                "departureTimeUtc": "2026-08-01T08:00:00Z", "arrivalTimeUtc": "2026-08-01T12:00:00Z",
                "busType": "AC Sleeper", "fare": {"amount": 500.00, "currency": "INR"}, "seatsAvailable": 10}]}
                """;
    }

    @Test
    void mapsASuccessfulResponseToProviderTrips() {
        mockServer.expect(requestToUriTemplate(TRIPS_URI, "Mumbai", "Pune", "2026-08-01"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(tripsBody(), MediaType.APPLICATION_JSON));

        List<ProviderTrip> trips = client.search(PROVIDER, CRITERIA);

        assertThat(trips).hasSize(1);
        assertThat(trips.getFirst().providerTripId()).isEqualTo("FB-1");
        assertThat(trips.getFirst().providerType()).isEqualTo(ProviderType.FLIXBUS);
        mockServer.verify();
    }

    @Test
    void sendsThePartnerTokenFromTheEncryptedStoreOnEveryRequest() {
        // The whole point of the credential wiring: the token reaching FlixBus is the one stored
        // in provider_credentials, not one read from configuration.
        mockServer.expect(requestToUriTemplate(TRIPS_URI, "Mumbai", "Pune", "2026-08-01"))
                .andExpect(header(FlixBusCredentials.AUTHENTICATION_HEADER, PARTNER_TOKEN))
                .andRespond(withSuccess(tripsBody(), MediaType.APPLICATION_JSON));

        client.search(PROVIDER, CRITERIA);

        mockServer.verify();
    }

    @Test
    void readsTheTokenPerCallSoARotatedCredentialTakesEffectImmediately() {
        // Caching the token in a field would keep calling FlixBus with a revoked secret until the
        // service restarted. Two clients over two stores stand in for the same store rotating.
        client = clientBackedBy(storeHolding("rotated-token-xyz"));

        mockServer.expect(requestToUriTemplate(TRIPS_URI, "Mumbai", "Pune", "2026-08-01"))
                .andExpect(header(FlixBusCredentials.AUTHENTICATION_HEADER, "rotated-token-xyz"))
                .andRespond(withSuccess(tripsBody(), MediaType.APPLICATION_JSON));

        client.search(PROVIDER, CRITERIA);

        mockServer.verify();
    }

    @Test
    void refusesToCallFlixBusWhenNoCredentialsAreStored() {
        // Fail closed. Calling FlixBus unauthenticated would surface as an opaque provider-side
        // 401, sending an operator hunting through logs for what is simply unconfigured setup.
        client = clientBackedBy(new InMemoryProviderCredentialsRepository());

        assertThatThrownBy(() -> client.search(PROVIDER, CRITERIA))
                .isInstanceOf(ProviderAuthenticationException.class);

        // No request was attempted at all.
        mockServer.verify();
    }

    @Test
    void translatesAServerErrorIntoProviderUnavailable() {
        mockServer.expect(requestToUriTemplate(TRIPS_URI, "Mumbai", "Pune", "2026-08-01"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search(PROVIDER, CRITERIA))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
