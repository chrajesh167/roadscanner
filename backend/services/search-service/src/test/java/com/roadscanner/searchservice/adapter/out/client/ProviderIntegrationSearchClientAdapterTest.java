package com.roadscanner.searchservice.adapter.out.client;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.domain.exception.ProviderSearchFailedException;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The federation hop: provider-integration-service's canonical response becoming this service's
 * models, and what happens when that service is unreachable.
 */
class ProviderIntegrationSearchClientAdapterTest {

    private static final String BASE_URL = "http://provider-integration.test";
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    private MockRestServiceServer mockServer;
    private ProviderTripSearchClient adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new ProviderIntegrationSearchClientAdapter(builder.build());
    }

    private static String tripsBody() {
        return """
                {"trips":[{
                  "providerTripId":"ride-1","providerType":"FLIXBUS","operatorName":"FlixBus",
                  "origin":"Hyderabad","destination":"Pune",
                  "departureTime":"2026-08-01T08:00:00Z","arrivalTime":"2026-08-01T14:00:00Z",
                  "serviceClass":"AC Sleeper","fareAmount":899.00,"fareCurrency":"INR","seatsAvailable":12,
                  "boardingPointId":"point-a","alightingPointId":"point-b"}]}""";
    }

    @Test
    void normalizesTheResponseIntoRoadScannerModels() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/internal/api/v1/providers/FLIXBUS/trips")))
                .andRespond(withSuccess(tripsBody(), MediaType.APPLICATION_JSON));

        List<ProviderTripResult> trips = adapter.search(FLIXBUS, "58291", "41100", DATE);

        assertThat(trips).hasSize(1);
        ProviderTripResult trip = trips.getFirst();
        assertThat(trip.providerCode()).isEqualTo("FLIXBUS");
        assertThat(trip.providerTripId()).isEqualTo("ride-1");
        assertThat(trip.route().origin()).isEqualTo("Hyderabad");
        assertThat(trip.route().destination()).isEqualTo("Pune");
        assertThat(trip.fare().amount()).isEqualByComparingTo(BigDecimal.valueOf(899.00));
        assertThat(trip.seatsAvailable()).isEqualTo(12);
        assertThat(trip.boardingPointIdIfPresent()).contains("point-a");
        mockServer.verify();
    }

    @Test
    void sendsTheProviderCityIdsAndDate() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/internal/api/v1/providers/FLIXBUS/trips")))
                .andExpect(queryParam("fromCityId", "58291"))
                .andExpect(queryParam("toCityId", "41100"))
                .andExpect(queryParam("departureDate", "2026-08-01"))
                .andRespond(withSuccess("{\"trips\":[]}", MediaType.APPLICATION_JSON));

        adapter.search(FLIXBUS, "58291", "41100", DATE);

        mockServer.verify();
    }

    @Test
    void reportsAnHttpErrorAsAFailedProviderSearch() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withServerError());

        // Reported rather than absorbed. Returning empty here made an outage look identical to a
        // provider that simply serves no trips, so the federation could not tell a caller its
        // answer was partial. Degradation still happens — one level up, where it can be recorded.
        assertThatThrownBy(() -> adapter.search(FLIXBUS, "58291", "41100", DATE))
                .isInstanceOf(ProviderSearchFailedException.class)
                .hasMessageContaining("FLIXBUS");
    }

    @Test
    void reportsATimeoutAsAFailedProviderSearch() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("read timed out");
                });

        assertThatThrownBy(() -> adapter.search(FLIXBUS, "58291", "41100", DATE))
                .isInstanceOf(ProviderSearchFailedException.class);
    }

    @Test
    void treatsAProviderWithNoTripsAsASuccessfulSearch() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("{\"trips\":[]}", MediaType.APPLICATION_JSON));

        // "Serves no trips on this route" is an answer, not a failure — it must never mark the
        // search incomplete.
        assertThat(adapter.search(FLIXBUS, "58291", "41100", DATE)).isEmpty();
    }

    @Test
    void toleratesAnEmptyOrAbsentTripsArray() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(adapter.search(FLIXBUS, "58291", "41100", DATE)).isEmpty();
    }

    @Test
    void ignoresFieldsProviderIntegrationMayAddLater() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        {"trips":[{
                          "providerTripId":"ride-1","providerType":"FLIXBUS","operatorName":"FlixBus",
                          "origin":"Hyderabad","destination":"Pune",
                          "departureTime":"2026-08-01T08:00:00Z","arrivalTime":"2026-08-01T14:00:00Z",
                          "serviceClass":"AC Sleeper","fareAmount":899.00,"fareCurrency":"INR","seatsAvailable":12,
                          "aFieldAddedNextQuarter":{"nested":true}}],"pagination":{"page":1}}""",
                        MediaType.APPLICATION_JSON));

        // A field added over there must not break search for every user here.
        assertThat(adapter.search(FLIXBUS, "58291", "41100", DATE)).hasSize(1);
    }

    @Test
    void carriesNoProviderSpecificVocabulary() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess(tripsBody(), MediaType.APPLICATION_JSON));

        ProviderTripResult trip = adapter.search(FLIXBUS, "58291", "41100", DATE).getFirst();

        // Everything crossing this boundary is a RoadScanner model. providerCode names who
        // answered; the station ids are opaque strings this service forwards but never reads.
        assertThat(trip).isInstanceOf(ProviderTripResult.class);
        assertThat(trip.providerCode()).isEqualTo("FLIXBUS");
    }
}
