package com.roadscanner.bookingservice.adapter.in.rest.ticket;

import com.roadscanner.bookingservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.bookingservice.config.SecurityConfig;
import com.roadscanner.bookingservice.domain.exception.TicketNotAvailableException;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.port.in.GetTicket;
import com.roadscanner.bookingservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, NoOpJwtDecoderConfig.class})
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetTicket getTicket;

    @Test
    void returnsTheTicketBase64Encoded() throws Exception {
        Ticket ticket = new Ticket("ticket-1", "PDF", "hello".getBytes(), Instant.parse("2026-08-01T00:00:00Z"));
        when(getTicket.get(any())).thenReturn(new GetTicket.Result(ticket));

        mockMvc.perform(get("/api/v1/bookings/{id}/ticket", UUID.randomUUID())
                        .with(jwt().jwt(b -> b.subject(UUID.randomUUID().toString()).claim("role", "TRAVELER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerTicketId").value("ticket-1"))
                .andExpect(jsonPath("$.format").value("PDF"));
    }

    @Test
    void returns404WhenNoTicketYet() throws Exception {
        BookingId bookingId = BookingId.generate();
        when(getTicket.get(any())).thenThrow(new TicketNotAvailableException(bookingId));

        mockMvc.perform(get("/api/v1/bookings/{id}/ticket", bookingId.value())
                        .with(jwt().jwt(b -> b.subject(UUID.randomUUID().toString()).claim("role", "TRAVELER"))))
                .andExpect(status().isNotFound());
    }
}
