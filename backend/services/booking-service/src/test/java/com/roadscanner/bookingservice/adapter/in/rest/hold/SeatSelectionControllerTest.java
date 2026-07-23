package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.roadscanner.bookingservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.bookingservice.config.SecurityConfig;
import com.roadscanner.bookingservice.domain.port.in.GetSeatSelectionView;
import com.roadscanner.bookingservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies {@code TRAVELER}-only enforcement on {@link SeatSelectionController} — added by
 * final implementation audit; the endpoint previously required authentication only, any role,
 * which disagreed with docs/services/booking-service/api-summary.md's documented "Requires:
 * TRAVELER". */
@WebMvcTest(SeatSelectionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, NoOpJwtDecoderConfig.class})
class SeatSelectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetSeatSelectionView getSeatSelectionView;

    @Test
    void travelerCanViewTheSeatSelection() throws Exception {
        when(getSeatSelectionView.get(any())).thenReturn(new GetSeatSelectionView.Result(List.of()));

        mockMvc.perform(get("/api/v1/bookings/trips/{tripId}/seats", UUID.randomUUID())
                        .with(jwt().jwt(b -> b.subject(UUID.randomUUID().toString()).claim("role", "TRAVELER"))))
                .andExpect(status().isOk());
    }

    @Test
    void operatorIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/trips/{tripId}/seats", UUID.randomUUID())
                        .with(jwt().jwt(b -> b.subject(UUID.randomUUID().toString()).claim("role", "OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsForbidden() throws Exception {
        // Deliberately not privileged-exempt here — this view is a pre-booking checkout-flow
        // resource, not booking data ADMIN/SUPPORT need to inspect for FR-8.3 support cases,
        // unlike Get Booking / Cancel Booking / Get Ticket.
        mockMvc.perform(get("/api/v1/bookings/trips/{tripId}/seats", UUID.randomUUID())
                        .with(jwt().jwt(b -> b.subject(UUID.randomUUID().toString()).claim("role", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/trips/{tripId}/seats", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
