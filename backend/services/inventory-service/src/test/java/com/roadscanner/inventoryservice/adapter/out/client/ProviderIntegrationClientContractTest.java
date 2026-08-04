package com.roadscanner.inventoryservice.adapter.out.client;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.inventoryservice.testsupport.ServiceContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The consumer half of the inter-service contract for this service: feeds the payloads checked
 * into {@code backend/contracts} — the exact bytes provider-integration-service is tested to emit —
 * through the real adapter, and asserts every field arrives in this service's models.
 *
 * <p>This service is the one the {@code busType} → {@code serviceClass} rename actually broke, and
 * it had no test covering this client at all, so the breakage reached {@code main} with both
 * suites green. These tests bind the shared file rather than a local literal, so the next rename
 * fails here instead of in production.
 *
 * <p>Every assertion checks a value, never just that the call returned — the wire records bind
 * leniently, so a renamed field yields null rather than an error.
 */
class ProviderIntegrationClientContractTest {

    private static final String BASE_URL = "http://provider-integration.test";
    private static final ProviderType FLIXBUS = new ProviderType("FLIXBUS");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    /** Before the session in the contract expires, so a cached session is considered live. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T08:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer mockServer;
    private ProviderIntegrationClient adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new ProviderIntegrationClientAdapter(builder.build(), CLOCK);
    }

    private static String contract(String name) {
        return ServiceContract.json("provider-integration-service/" + name);
    }

    /**
     * Every call here authenticates first, so the session contract is exercised by each test
     * rather than needing a case of its own — and a break in it fails them all loudly.
     */
    private void expectAuthentication() {
        mockServer.expect(requestTo(BASE_URL + "/internal/api/v1/providers/FLIXBUS/sessions"))
                .andRespond(withSuccess(contract("authenticate-provider-response.json"), MediaType.APPLICATION_JSON));
    }

    private void expectSeatMap() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/trips/ride-1/seat-map")))
                .andRespond(withSuccess(contract("seat-map-response.json"), MediaType.APPLICATION_JSON));
    }

    @Test
    void everyFieldOfThePublishedTripContractBindsIntoTheDomainModel() {
        expectAuthentication();
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/internal/api/v1/providers/FLIXBUS/sessions/")))
                .andRespond(withSuccess(contract("search-trips-response.json"), MediaType.APPLICATION_JSON));

        List<ProviderIntegrationClient.ExternalProviderTrip> trips =
                adapter.searchTrips(FLIXBUS, "Hyderabad", "Pune", DATE);

        assertThat(trips).hasSize(2);
        ProviderIntegrationClient.ExternalProviderTrip trip = trips.getFirst();

        assertThat(trip.providerTripId()).isEqualTo("ride-1");
        assertThat(trip.operatorName()).isEqualTo("FlixBus");
        assertThat(trip.origin()).isEqualTo("Hyderabad");
        assertThat(trip.destination()).isEqualTo("Pune");
        assertThat(trip.departureTime()).isEqualTo(Instant.parse("2026-08-01T08:00:00Z"));
        assertThat(trip.arrivalTime()).isEqualTo(Instant.parse("2026-08-01T14:00:00Z"));
        assertThat(trip.fareAmount()).isEqualByComparingTo(new BigDecimal("899.00"));
        assertThat(trip.fareCurrency()).isEqualTo("INR");
        assertThat(trip.seatsAvailable()).isEqualTo(12);

        // The exact field the rename broke: the contract publishes serviceClass, and this service
        // carries it in its own first-party vocabulary. Null here means the binding has drifted.
        assertThat(trip.busType()).isEqualTo("AC Sleeper");

        mockServer.verify();
    }

    @Test
    void aTripWithNoServiceTierStillBinds() {
        expectAuthentication();
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/internal/api/v1/providers/FLIXBUS/sessions/")))
                .andRespond(withSuccess(contract("search-trips-response.json"), MediaType.APPLICATION_JSON));

        ProviderIntegrationClient.ExternalProviderTrip sparse =
                adapter.searchTrips(FLIXBUS, "Hyderabad", "Pune", DATE).get(1);

        assertThat(sparse.providerTripId()).isEqualTo("ride-2");
        assertThat(sparse.busType()).isNull();
        assertThat(sparse.seatsAvailable()).isZero();
    }

    @Test
    void everyFieldOfThePublishedSeatMapContractBindsIntoTheDomainModel() {
        expectAuthentication();
        expectSeatMap();

        Optional<ProviderIntegrationClient.ExternalSeatLayout> layout = adapter.getSeatLayout(FLIXBUS, "ride-1");

        assertThat(layout).isPresent();
        assertThat(layout.orElseThrow().providerTripId()).isEqualTo("ride-1");
        assertThat(layout.orElseThrow().seats()).hasSize(3);

        ProviderIntegrationClient.ExternalSeat seat = layout.orElseThrow().seats().getFirst();
        assertThat(seat.seatNumber()).isEqualTo("1A");
        assertThat(seat.deck()).isEqualTo("LOWER");
        assertThat(seat.seatType()).isEqualTo("SLEEPER");

        mockServer.verify();
    }

    @Test
    void availabilityIsDerivedFromThePublishedSeatStatusVocabulary() {
        expectAuthentication();
        expectSeatMap();

        // Counted by string-matching "AVAILABLE" against the contract's status field. If that
        // vocabulary ever changed — or the enum were published as an ordinal — this would report
        // zero and quietly show every trip as sold out. The contract has 2 available of 3.
        assertThat(adapter.getAvailableSeatCount(FLIXBUS, "ride-1")).isEqualTo(OptionalInt.of(2));

        mockServer.verify();
    }
}
