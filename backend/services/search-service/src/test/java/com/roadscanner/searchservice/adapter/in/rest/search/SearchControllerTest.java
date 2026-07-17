package com.roadscanner.searchservice.adapter.in.rest.search;

import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.searchservice.config.SearchProperties;
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
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, SearchControllerTest.TestConfig.class})
class SearchControllerTest {

    private static final int MAX_PAGE_SIZE = 5;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchTrips searchTrips;

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
}
