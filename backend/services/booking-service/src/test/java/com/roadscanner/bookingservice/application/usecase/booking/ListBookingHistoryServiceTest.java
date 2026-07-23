package com.roadscanner.bookingservice.application.usecase.booking;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.ListBookingHistory;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListBookingHistoryServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final ListBookingHistoryService service = new ListBookingHistoryService(bookingRepository);

    private void save(UUID travelerId) {
        bookingRepository.save(Booking.create(BookingId.generate(), travelerId, new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", UUID.randomUUID().toString(),
                T0.plusSeconds(600), List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0));
    }

    @Test
    void returnsOnlyTheRequestersOwnBookings() {
        UUID travelerId = UUID.randomUUID();
        save(travelerId);
        save(travelerId);
        save(UUID.randomUUID());

        ListBookingHistory.Result result = service.list(
                new ListBookingHistory.Command(new RequesterContext(travelerId, Role.TRAVELER), Optional.empty()));

        assertThat(result.bookings()).hasSize(2);
    }

    @Test
    void travelerCannotOverrideOnBehalfOf() {
        UUID travelerId = UUID.randomUUID();
        save(travelerId);
        UUID someoneElse = UUID.randomUUID();
        save(someoneElse);

        ListBookingHistory.Result result = service.list(new ListBookingHistory.Command(
                new RequesterContext(travelerId, Role.TRAVELER), Optional.of(someoneElse)));

        assertThat(result.bookings()).hasSize(1);
    }

    @Test
    void adminCanListOnBehalfOfAnotherTraveler() {
        UUID targetTraveler = UUID.randomUUID();
        save(targetTraveler);

        ListBookingHistory.Result result = service.list(new ListBookingHistory.Command(
                new RequesterContext(UUID.randomUUID(), Role.ADMIN), Optional.of(targetTraveler)));

        assertThat(result.bookings()).hasSize(1);
    }
}
