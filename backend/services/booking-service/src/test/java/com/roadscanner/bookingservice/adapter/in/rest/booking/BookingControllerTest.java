package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.bookingservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.bookingservice.config.SecurityConfig;
import com.roadscanner.bookingservice.domain.exception.BookingNotFoundException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.CancelBooking;
import com.roadscanner.bookingservice.domain.port.in.CreateBooking;
import com.roadscanner.bookingservice.domain.port.in.GetBooking;
import com.roadscanner.bookingservice.domain.port.in.ListBookingHistory;
import com.roadscanner.bookingservice.domain.port.in.ListTripBookings;
import com.roadscanner.bookingservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, NoOpJwtDecoderConfig.class})
class BookingControllerTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateBooking createBooking;

    @MockBean
    private GetBooking getBooking;

    @MockBean
    private ListBookingHistory listBookingHistory;

    @MockBean
    private ListTripBookings listTripBookings;

    @MockBean
    private CancelBooking cancelBooking;

    private RequestPostProcessor traveler(UUID subject) {
        return jwt().jwt(builder -> builder.subject(subject.toString()).claim("role", "TRAVELER"));
    }

    private Booking sampleBooking(UUID travelerId) {
        return Booking.create(BookingId.generate(), travelerId, new TripId(UUID.randomUUID()), T0.plusSeconds(3600),
                new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
    }

    @Test
    void createReturns201() throws Exception {
        UUID travelerId = UUID.randomUUID();
        BookingId bookingId = BookingId.generate();
        when(createBooking.create(any())).thenReturn(new CreateBooking.Result(bookingId, BookingStatus.PENDING_PAYMENT));

        String body = """
                {"seatHoldId":"%s","passengers":[{"fullName":"Jane Doe","age":30,"gender":"F","seatNumber":"L1"}]}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/bookings").with(traveler(travelerId))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void getReturnsTheBooking() throws Exception {
        UUID travelerId = UUID.randomUUID();
        Booking booking = sampleBooking(travelerId);
        when(getBooking.get(any())).thenReturn(new GetBooking.Result(booking));

        mockMvc.perform(get("/api/v1/bookings/{id}", booking.id().value()).with(traveler(travelerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(booking.id().toString()))
                .andExpect(jsonPath("$.passengers[0].fullName").value("Jane Doe"));
    }

    @Test
    void getReturns404ForUnknownOrUnauthorizedBooking() throws Exception {
        BookingId bookingId = BookingId.generate();
        when(getBooking.get(any())).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId.value()).with(traveler(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsBookingHistoryByDefault() throws Exception {
        UUID travelerId = UUID.randomUUID();
        when(listBookingHistory.list(any())).thenReturn(new ListBookingHistory.Result(List.of(sampleBooking(travelerId))));

        mockMvc.perform(get("/api/v1/bookings").with(traveler(travelerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings").isArray())
                .andExpect(jsonPath("$.bookings.length()").value(1));
    }

    @Test
    void listWithTripIdRoutesToListTripBookings() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(listTripBookings.list(any())).thenReturn(new ListTripBookings.Result(List.of()));

        mockMvc.perform(get("/api/v1/bookings?tripId={tripId}", tripId).with(traveler(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings").isArray());
    }

    @Test
    void cancelReturnsUpdatedStatus() throws Exception {
        when(cancelBooking.cancel(any())).thenReturn(new CancelBooking.Result(BookingStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", UUID.randomUUID()).with(traveler(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
    }
}
