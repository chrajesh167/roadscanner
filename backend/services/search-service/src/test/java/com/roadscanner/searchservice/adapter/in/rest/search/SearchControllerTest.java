package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.searchservice.config.SearchProperties;
import com.roadscanner.searchservice.config.SecurityConfig;
import com.roadscanner.searchservice.testsupport.security.NoOpJwtDecoderConfig;
import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.model.TripSearchResult;
import com.roadscanner.searchservice.domain.port.in.SearchTrips;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SearchProperties} is a record (implicitly final) and is supplied here as a real
 * instance via {@link TestConfig} rather than a {@code @MockBean} — Mockito's inline mock maker
 * for final classes/records is not active in this project's test setup, and there is no
 * behavior on a plain configuration record worth mocking anyway.
 */
@WebMvcTest(SearchController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, SearchControllerTest.TestConfig.class, SecurityConfig.class, NoOpJwtDecoderConfig.class})
class SearchControllerTest {

    private static final int MAX_PAGE_SIZE = 5;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchTrips searchTrips;

    @MockBean
    private com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips searchProviderTrips;

    @MockBean
    private com.roadscanner.searchservice.domain.port.out.CatalogTripResolver catalogTripResolver;

    @TestConfiguration
    static class TestConfig {
        @Bean
        SearchProperties searchProperties() {
            return new SearchProperties(
                    new SearchProperties.Pagination(MAX_PAGE_SIZE, MAX_PAGE_SIZE),
                    new SearchProperties.Suggestions(10),
                    new SearchProperties.Availability(Duration.ofSeconds(15), Duration.ofMillis(500)),
                    new SearchProperties.Kafka("trip-events", "review-events"));
        }
    }

    private SearchableTrip sampleTrip() {
        return SearchableTrip.publish(new TripId(UUID.randomUUID()), new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of("WiFi")),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z"));
    }

    @org.junit.jupiter.api.BeforeEach
    void federationReturnsNothingUnlessATestSaysOtherwise() {
        when(searchProviderTrips.search(any())).thenReturn(
                com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips.Result.empty());
        // No provider trip resolves to a catalog trip unless a test says otherwise: the default is
        // the honest one for a departure catalog sync has not imported.
        when(catalogTripResolver.resolveCatalogTripIds(any())).thenReturn(java.util.Map.of());
    }

    @Test
    void returnsSearchResultsForAValidQuery() throws Exception {
        SearchableTrip trip = sampleTrip();
        ResultPage<TripSearchResult> page = ResultPage.of(
                List.of(new TripSearchResult(trip, AvailabilityStatus.of(10))), 0, 20, 1);
        when(searchTrips.search(any())).thenReturn(new SearchTrips.SearchTripsResult(page));

        mockMvc.perform(get("/api/v1/search/trips")
                        .param("origin", "Mumbai")
                        .param("destination", "Pune")
                        .param("date", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tripId").value(trip.tripId().toString()))
                .andExpect(jsonPath("$.content[0].origin").value("Mumbai"))
                .andExpect(jsonPath("$.content[0].availableSeats").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void missingRequiredParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search/trips").param("origin", "Mumbai"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankOriginReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search/trips")
                        .param("origin", "  ")
                        .param("destination", "Pune")
                        .param("date", "2026-08-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestedSizeIsClampedToTheConfiguredMaximum() throws Exception {
        when(searchTrips.search(any())).thenReturn(
                new SearchTrips.SearchTripsResult(ResultPage.of(List.of(), 0, MAX_PAGE_SIZE, 0)));

        mockMvc.perform(get("/api/v1/search/trips")
                        .param("origin", "Mumbai")
                        .param("destination", "Pune")
                        .param("date", "2026-08-01")
                        .param("size", "1000"))
                .andExpect(status().isOk());

        verify(searchTrips).search(argThat(command -> command.query().size() == MAX_PAGE_SIZE));
    }

    /**
     * The link that makes a provider trip actionable, and that lets a caller recognise an indexed
     * trip in the same response as this same departure rather than a second bus.
     */
    @Test
    void exposesTheCatalogTripBackingAProviderTripSoItCanBeSelectedAndRecognised() throws Exception {
        UUID catalogTripId = UUID.randomUUID();
        String providerTripId = "MOCK-HYDERABAD-BENGALURU-2026-08-13-AC-SLEEPER";
        when(searchTrips.search(any())).thenReturn(
                new SearchTrips.SearchTripsResult(ResultPage.of(List.of(), 0, MAX_PAGE_SIZE, 0)));
        when(searchProviderTrips.search(any())).thenReturn(
                new com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips.Result(
                        List.of(providerTrip(providerTripId)),
                        java.util.Set.of(new com.roadscanner.searchservice.location.domain.model.ProviderCode("MOCK")),
                        java.util.Set.of(new com.roadscanner.searchservice.location.domain.model.ProviderCode("MOCK")),
                        java.util.Set.of()));
        when(catalogTripResolver.resolveCatalogTripIds(any()))
                .thenReturn(java.util.Map.of(providerTripId, catalogTripId));

        mockMvc.perform(get("/api/v1/search/trips")
                        .param("origin", "Hyderabad")
                        .param("destination", "Bengaluru")
                        .param("date", "2026-08-13")
                        .param("originLocationId", UUID.randomUUID().toString())
                        .param("destinationLocationId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerTrips[0].providerCode").value("MOCK"))
                .andExpect(jsonPath("$.providerTrips[0].providerTripId").value(providerTripId))
                .andExpect(jsonPath("$.providerTrips[0].catalogTripId").value(catalogTripId.toString()))
                .andExpect(jsonPath("$.providerSearchComplete").value(true));
    }

    @Test
    void aProviderTripCatalogSyncHasNotImportedReportsNoCatalogTripRatherThanAnInventedOne() throws Exception {
        when(searchTrips.search(any())).thenReturn(
                new SearchTrips.SearchTripsResult(ResultPage.of(List.of(), 0, MAX_PAGE_SIZE, 0)));
        when(searchProviderTrips.search(any())).thenReturn(
                new com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips.Result(
                        List.of(providerTrip("MOCK-UNIMPORTED-2026-12-31-AC-SLEEPER")),
                        java.util.Set.of(new com.roadscanner.searchservice.location.domain.model.ProviderCode("MOCK")),
                        java.util.Set.of(new com.roadscanner.searchservice.location.domain.model.ProviderCode("MOCK")),
                        java.util.Set.of()));
        when(catalogTripResolver.resolveCatalogTripIds(any())).thenReturn(java.util.Map.of());

        // Shown, because the departure is real; not selectable, because nothing can be booked
        // against it. A fabricated id here would only move the failure to the seat map.
        mockMvc.perform(get("/api/v1/search/trips")
                        .param("origin", "Hyderabad")
                        .param("destination", "Bengaluru")
                        .param("date", "2026-12-31")
                        .param("originLocationId", UUID.randomUUID().toString())
                        .param("destinationLocationId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerTrips[0].catalogTripId").doesNotExist());
    }

    @Test
    void aFailedProviderStillReportsAnIncompleteAnswerAlongsideTheTripsThatDidArrive() throws Exception {
        when(searchTrips.search(any())).thenReturn(
                new SearchTrips.SearchTripsResult(ResultPage.of(List.of(), 0, MAX_PAGE_SIZE, 0)));
        when(searchProviderTrips.search(any())).thenReturn(
                new com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips.Result(
                        List.of(providerTrip("MOCK-HYDERABAD-BENGALURU-2026-08-13-AC-SLEEPER")),
                        java.util.Set.of(new com.roadscanner.searchservice.location.domain.model.ProviderCode("MOCK"),
                                new com.roadscanner.searchservice.location.domain.model.ProviderCode("FLIXBUS")),
                        java.util.Set.of(new com.roadscanner.searchservice.location.domain.model.ProviderCode("MOCK")),
                        java.util.Set.of(new com.roadscanner.searchservice.location.domain.model.ProviderCode("FLIXBUS"))));

        mockMvc.perform(get("/api/v1/search/trips")
                        .param("origin", "Hyderabad")
                        .param("destination", "Bengaluru")
                        .param("date", "2026-08-13")
                        .param("originLocationId", UUID.randomUUID().toString())
                        .param("destinationLocationId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerTrips.length()").value(1))
                .andExpect(jsonPath("$.providerSearchComplete").value(false));
    }

    private com.roadscanner.searchservice.domain.model.ProviderTripResult providerTrip(String providerTripId) {
        return new com.roadscanner.searchservice.domain.model.ProviderTripResult(
                "MOCK", providerTripId, "Mock Travels",
                new Route("Hyderabad", "Bengaluru"),
                new Schedule(Instant.parse("2026-08-13T20:00:00Z"), Instant.parse("2026-08-14T02:00:00Z")),
                "AC Sleeper",
                new FareSnapshot(new BigDecimal("899.00"), Currency.getInstance("INR")),
                29, "mock-point-Hyderabad", "mock-point-Bengaluru");
    }
}
