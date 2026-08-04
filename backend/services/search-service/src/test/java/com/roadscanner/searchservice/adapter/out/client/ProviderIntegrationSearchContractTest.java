package com.roadscanner.searchservice.adapter.out.client;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.testsupport.ServiceContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The consumer half of the inter-service contract: feeds the payload checked into
 * {@code backend/contracts} — the exact bytes provider-integration-service is tested to emit —
 * through this service's real HTTP client, and asserts every field arrives.
 *
 * <p>Distinct from {@link ProviderIntegrationSearchClientAdapterTest}, which exercises this
 * adapter's own behaviour (degradation, empty bodies) against a JSON literal it writes itself. A
 * literal proves only that this service agrees with itself; when {@code busType} was renamed to
 * {@code serviceClass}, every such test on both sides stayed green while the integration was
 * broken, because each side had quietly updated its own copy of the JSON. Binding the shared file
 * is what makes that impossible: a rename on either side leaves a null here.
 *
 * <p>Asserts on non-null values rather than merely that a call succeeded — {@code ignoreUnknown}
 * means a renamed field does not throw, it silently produces null, so "it parsed" proves nothing.
 */
class ProviderIntegrationSearchContractTest {

    private static final String BASE_URL = "http://provider-integration.test";
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");

    private MockRestServiceServer mockServer;
    private ProviderTripSearchClient adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new ProviderIntegrationSearchClientAdapter(builder.build());
    }

    private List<ProviderTripResult> searchAgainstTheContract() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/internal/api/v1/providers/FLIXBUS/trips")))
                .andRespond(withSuccess(ServiceContract.json("provider-integration-service/search-trips-response.json"),
                        MediaType.APPLICATION_JSON));
        return adapter.search(FLIXBUS, "58291", "41100", LocalDate.of(2026, 8, 1));
    }

    @Test
    void everyFieldOfThePublishedTripContractBindsIntoTheDomainModel() {
        List<ProviderTripResult> trips = searchAgainstTheContract();

        assertThat(trips).hasSize(2);
        ProviderTripResult trip = trips.getFirst();

        assertThat(trip.providerCode()).isEqualTo("FLIXBUS");
        assertThat(trip.providerTripId()).isEqualTo("ride-1");
        assertThat(trip.operatorName()).isEqualTo("FlixBus");
        assertThat(trip.route().origin()).isEqualTo("Hyderabad");
        assertThat(trip.route().destination()).isEqualTo("Pune");
        assertThat(trip.schedule().departureTime()).isEqualTo(Instant.parse("2026-08-01T08:00:00Z"));
        assertThat(trip.schedule().arrivalTime()).isEqualTo(Instant.parse("2026-08-01T14:00:00Z"));
        assertThat(trip.serviceClass()).isEqualTo("AC Sleeper");
        assertThat(trip.fare().amount()).isEqualByComparingTo(new BigDecimal("899.00"));
        assertThat(trip.fare().currency()).isEqualTo(Currency.getInstance("INR"));
        assertThat(trip.seatsAvailable()).isEqualTo(12);
        assertThat(trip.boardingPointIdIfPresent()).contains("point-a");
        assertThat(trip.alightingPointIdIfPresent()).contains("point-b");

        mockServer.verify();
    }

    @Test
    void theOptionalHalfOfTheContractBindsWithoutDroppingTheTrip() {
        // A provider reporting no service tier and no separate boarding points is a valid trip, not
        // a malformed one — and a sold-out trip still belongs in the results, marked sold out.
        ProviderTripResult sparse = searchAgainstTheContract().get(1);

        assertThat(sparse.providerTripId()).isEqualTo("ride-2");
        assertThat(sparse.serviceClass()).isNull();
        assertThat(sparse.boardingPointIdIfPresent()).isEmpty();
        assertThat(sparse.alightingPointIdIfPresent()).isEmpty();
        assertThat(sparse.seatsAvailable()).isZero();
        assertThat(sparse.fare().amount()).isEqualByComparingTo(new BigDecimal("1250.50"));
    }
}
