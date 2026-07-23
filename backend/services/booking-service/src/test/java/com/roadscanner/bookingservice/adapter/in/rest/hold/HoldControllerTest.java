package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.bookingservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.bookingservice.config.SecurityConfig;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.port.in.HoldSeats;
import com.roadscanner.bookingservice.domain.port.in.ReleaseHold;
import com.roadscanner.bookingservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HoldController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, NoOpJwtDecoderConfig.class})
class HoldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HoldSeats holdSeats;

    @MockBean
    private ReleaseHold releaseHold;

    private org.springframework.test.web.servlet.request.RequestPostProcessor traveler(UUID subject) {
        return jwt().jwt(builder -> builder.subject(subject.toString()).claim("role", "TRAVELER"));
    }

    @Test
    void holdReturns201WithReferenceAndExpiry() throws Exception {
        UUID tripId = UUID.randomUUID();
        SeatHoldId seatHoldId = SeatHoldId.generate();
        Instant expiresAt = Instant.parse("2026-08-01T00:10:00Z");
        when(holdSeats.hold(any())).thenReturn(new HoldSeats.Result(seatHoldId, List.of("L1"), expiresAt));

        mockMvc.perform(post("/api/v1/bookings/holds")
                        .with(traveler(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HoldSeatsRequest(tripId, List.of("L1")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatHoldId").value(seatHoldId.toString()))
                .andExpect(jsonPath("$.seatNumbers[0]").value("L1"));
    }

    @Test
    void holdRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/holds")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new HoldSeatsRequest(UUID.randomUUID(), List.of("L1")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void holdRejectsEmptySeatNumbers() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/holds")
                        .with(traveler(UUID.randomUUID()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HoldSeatsRequest(UUID.randomUUID(), List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonTravelerCannotHoldSeats() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/holds")
                        .with(jwt().jwt(b -> b.subject(UUID.randomUUID().toString()).claim("role", "OPERATOR")))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new HoldSeatsRequest(UUID.randomUUID(), List.of("L1")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void releaseReturnsWhetherItWasReleased() throws Exception {
        when(releaseHold.release(any())).thenReturn(new ReleaseHold.Result(true));

        mockMvc.perform(delete("/api/v1/bookings/holds/{seatHoldId}", UUID.randomUUID())
                        .with(traveler(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(true));
    }
}
