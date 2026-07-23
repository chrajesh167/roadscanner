package com.roadscanner.bookingservice.application.usecase.ticket;

import com.roadscanner.bookingservice.domain.exception.TicketNotAvailableException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.GetTicket;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetTicketServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final GetTicketService service = new GetTicketService(bookingRepository);

    private Booking newBooking(UUID travelerId) {
        Booking booking = Booking.create(BookingId.generate(), travelerId, new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(booking);
        return booking;
    }

    @Test
    void returnsTheTicketOnceConfirmed() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = newBooking(travelerId);
        Ticket ticket = new Ticket("ticket-1", "PDF", "content".getBytes(), T0.plusSeconds(10));
        booking.confirm("provider-ref-1", ticket, T0.plusSeconds(10));
        bookingRepository.save(booking);

        GetTicket.Result result = service.get(
                new GetTicket.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER)));

        assertThat(result.ticket()).isEqualTo(ticket);
    }

    @Test
    void failsWhenNotYetConfirmed() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = newBooking(travelerId);

        assertThatThrownBy(() -> service.get(
                new GetTicket.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER))))
                .isInstanceOf(TicketNotAvailableException.class);
    }
}
