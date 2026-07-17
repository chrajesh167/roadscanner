package com.roadscanner.searchservice.adapter.in.rest.detail;

import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.domain.exception.TripNotFoundException;
import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.model.TripSearchResult;
import com.roadscanner.searchservice.domain.port.in.GetTripDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripDetailController.class)
@Import(GlobalExceptionHandler.class)
class TripDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetTripDetail getTripDetail;

    @Test
    void returnsTheTripWhenIndexed() throws Exception {
        TripId tripId = new TripId(UUID.randomUUID());
        SearchableTrip trip = SearchableTrip.publish(tripId, new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z"));
        when(getTripDetail.getDetail(any())).thenReturn(new GetTripDetail.GetTripDetailResult(
                new TripSearchResult(trip, AvailabilityStatus.of(4))));

        mockMvc.perform(get("/api/v1/search/trips/{tripId}", tripId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.availableSeats").value(4));
    }

    @Test
    void returnsNotFoundForAnUnindexedTrip() throws Exception {
        TripId tripId = new TripId(UUID.randomUUID());
        when(getTripDetail.getDetail(any())).thenThrow(new TripNotFoundException(tripId));

        mockMvc.perform(get("/api/v1/search/trips/{tripId}", tripId.value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedTripIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search/trips/{tripId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
