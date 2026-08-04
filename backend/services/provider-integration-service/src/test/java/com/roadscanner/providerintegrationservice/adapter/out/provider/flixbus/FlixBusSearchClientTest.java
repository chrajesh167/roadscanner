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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Endpoint 2 against the documented contract: {@code GET /public/v2/trip/search}, partner token
 * only, {@code dd.MM.yyyy} departure date, and the nested {@code trips[].items[].legs[]} response.
 *
 * <p>The assertions that matter most here are the ones a plausible-looking implementation gets
 * wrong: the date format is not ISO, interconnection trips must not be offered, and the three leg
 * UUIDs must survive into the trip id or the trip cannot later be booked.
 */
class FlixBusSearchClientTest {

    private static final String BASE_URL = "http://flixbus.test";
    private static final Instant FIXED = Instant.parse("2026-07-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);
    private static final String PARTNER_TOKEN = "partner-token-abc";

    private static final String RIDE_ID = "9e0f2b3c-1111-4a2b-8c3d-000000000001";
    private static final String FROM_STATION = "3da253ae-02ca-430c-87e5-22842065a77d";
    private static final String TO_STATION = "2e46c6ce-d031-46f2-8ab5-e41038b8a029";

    private static final Provider PROVIDER = Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS,
            ProviderCategory.BUS, "FlixBus", true, Set.of(), BASE_URL, 5_000, 2, FIXED, FIXED);

    private static final SearchCriteria CRITERIA =
            new SearchCriteria("hyderabad-city-id", "bengaluru-city-id", LocalDate.of(2026, 8, 1));

    private MockRestServiceServer mockServer;
    private FlixBusSearchClient client;

    private static FlixBusProperties properties() {
        return new FlixBusProperties(BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(15), "INR", "+91",
                Duration.ofHours(23), 3, Duration.ofMillis(1), "offline", "cash");
    }

    private static FlixBusCredentials credentialsHolding(String partnerToken) {
        InMemoryProviderCredentialsRepository repository = new InMemoryProviderCredentialsRepository();
        if (partnerToken != null) {
            repository.save(ProviderCredentials.issue(ProviderCredentialsId.generate(), PROVIDER.id(),
                    "partner@roadscanner.com", "s3cret", partnerToken, FIXED));
        }
        return new FlixBusCredentials(repository);
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new FlixBusSearchClient(builder.build(), new FlixBusMapper(CLOCK),
                new FlixBusExceptionTranslator(CLOCK), credentialsHolding(PARTNER_TOKEN), properties());
    }

    private static String searchBody() {
        return """
                {"trips":[{
                  "from":{"id":"hyderabad-city-id","name":"Hyderabad"},
                  "to":{"id":"bengaluru-city-id","name":"Bengaluru"},
                  "items":[
                    {"type":"direct","transfer_type":"semi-bed","price_total_sum":1299.0,
                     "available":{"seats":21},
                     "departure":{"timestamp":1785484800},
                     "arrival":{"timestamp":1785517800},
                     "legs":[{"ride_id":"%s","from_station_id":"%s","to_station_id":"%s"}]},
                    {"type":"interconnection","transfer_type":"bed","price_total_sum":999.0,
                     "available":{"seats":5},
                     "departure":{"timestamp":1785484800},
                     "arrival":{"timestamp":1785528600},
                     "legs":[{"ride_id":"other-ride","from_station_id":"a","to_station_id":"b"}]}
                  ]}]}""".formatted(RIDE_ID, FROM_STATION, TO_STATION);
    }

    private void expectSearch() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/public/v2/trip/search")))
                .andExpect(header(FlixBusCredentials.AUTHENTICATION_HEADER, PARTNER_TOKEN))
                .andRespond(withSuccess(searchBody(), MediaType.APPLICATION_JSON));
    }

    @Test
    void sendsTheDocumentedQueryParameters() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/public/v2/trip/search")))
                .andExpect(queryParam("from_city_id", "hyderabad-city-id"))
                .andExpect(queryParam("to_city_id", "bengaluru-city-id"))
                // dd.MM.yyyy, not ISO. Sending 2026-08-01 here returns trips for the wrong day or
                // none at all, and nothing in the response says the date was misread.
                .andExpect(queryParam("departure_date", "01.08.2026"))
                .andExpect(queryParam("search_by", "cities"))
                .andExpect(queryParam("currency", "INR"))
                .andRespond(withSuccess(searchBody(), MediaType.APPLICATION_JSON));

        client.search(PROVIDER, CRITERIA);

        mockServer.verify();
    }

    @Test
    void mapsADirectTripOntoTheProviderNeutralModel() {
        expectSearch();

        List<ProviderTrip> trips = client.search(PROVIDER, CRITERIA);

        assertThat(trips).hasSize(1);
        ProviderTrip trip = trips.getFirst();
        assertThat(trip.providerType()).isEqualTo(ProviderType.FLIXBUS);
        assertThat(trip.operatorName()).isEqualTo("FlixBus");
        assertThat(trip.origin()).isEqualTo("Hyderabad");
        assertThat(trip.destination()).isEqualTo("Bengaluru");
        assertThat(trip.serviceClass()).isEqualTo("semi-bed");
        assertThat(trip.fare().amount()).isEqualByComparingTo(BigDecimal.valueOf(1299.0));
        assertThat(trip.seatsAvailable()).isEqualTo(21);
        // Epoch seconds, not millis: reading these as millis dates every trip to 1970.
        assertThat(trip.departureTime()).isEqualTo(Instant.ofEpochSecond(1785484800L));
        assertThat(trip.arrivalTime()).isEqualTo(Instant.ofEpochSecond(1785517800L));
    }

    @Test
    void carriesAllThreeLegIdentifiersInTheTripId() {
        expectSearch();

        ProviderTrip trip = client.search(PROVIDER, CRITERIA).getFirst();

        // These three UUIDs are required by the seat-map and cart calls and cannot be recovered
        // later. Encoding them in the trip id is what makes a search result bookable minutes on.
        assertThat(trip.providerTripId()).isEqualTo("direct:" + RIDE_ID + ":" + FROM_STATION + ":" + TO_STATION);
        assertThat(trip.boardingPointId()).isEqualTo(FROM_STATION);
        assertThat(trip.alightingPointId()).isEqualTo(TO_STATION);

        FlixBusTripUid parsed = FlixBusTripUid.parse(trip.providerTripId());
        assertThat(parsed.rideId()).isEqualTo(RIDE_ID);
        assertThat(parsed.fromStationId()).isEqualTo(FROM_STATION);
        assertThat(parsed.toStationId()).isEqualTo(TO_STATION);
    }

    @Test
    void dropsInterconnectionTripsThisIntegrationCannotBook() {
        expectSearch();

        // The payload carries one direct and one interconnection trip. Offering a multi-leg trip
        // this integration cannot book is worse than not showing it: the traveller only finds out
        // at checkout.
        assertThat(client.search(PROVIDER, CRITERIA))
                .extracting(ProviderTrip::providerTripId)
                .allSatisfy(id -> assertThat(id).startsWith("direct:"));
    }

    @Test
    void refusesToSearchWhenNoPartnerTokenIsStored() {
        client = new FlixBusSearchClient(RestClient.builder().baseUrl(BASE_URL).build(), new FlixBusMapper(CLOCK),
                new FlixBusExceptionTranslator(CLOCK), credentialsHolding(null), properties());

        assertThatThrownBy(() -> client.search(PROVIDER, CRITERIA))
                .isInstanceOf(ProviderAuthenticationException.class);
    }

    @Test
    void translatesAServerErrorIntoProviderUnavailable() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/public/v2/trip/search")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search(PROVIDER, CRITERIA))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
