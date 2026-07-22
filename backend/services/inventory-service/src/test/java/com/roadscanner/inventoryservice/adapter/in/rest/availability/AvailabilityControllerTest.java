package com.roadscanner.inventoryservice.adapter.in.rest.availability;

import com.roadscanner.inventoryservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.GetTripAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the one frozen contract in this service — {@code search-service}'s
 * {@code AvailabilityClient} already calls this exact path expecting this exact response shape
 * (see {@link AvailabilityController}'s Javadoc). Any change to the JSON field name, the path, or
 * the known/unknown status codes here is a breaking change to a service outside this repository
 * module's own test suite. */
@WebMvcTest(AvailabilityController.class)
@Import(GlobalExceptionHandler.class)
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetTripAvailability getTripAvailability;

    @Test
    void returnsAvailableSeatsAsTheExactFrozenShapeWhenKnown() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(getTripAvailability.get(any())).thenReturn(GetTripAvailability.Result.known(7));

        mockMvc.perform(get("/api/v1/inventory/trips/{tripId}/availability", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(7));
    }

    @Test
    void returns503WhenAvailabilityIsUnknownRatherThanA200WithASentinel() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(getTripAvailability.get(any())).thenReturn(GetTripAvailability.Result.unknown());

        mockMvc.perform(get("/api/v1/inventory/trips/{tripId}/availability", tripId))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void malformedTripIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/trips/{tripId}/availability", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passesTheParsedTripIdThroughToThePort() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(getTripAvailability.get(any())).thenReturn(GetTripAvailability.Result.known(3));

        mockMvc.perform(get("/api/v1/inventory/trips/{tripId}/availability", tripId))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(getTripAvailability)
                .get(new GetTripAvailability.Command(new TripId(tripId)));
    }
}
