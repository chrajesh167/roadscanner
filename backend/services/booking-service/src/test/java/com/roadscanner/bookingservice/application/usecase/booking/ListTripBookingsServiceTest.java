package com.roadscanner.bookingservice.application.usecase.booking;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.ListTripBookings;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import com.roadscanner.bookingservice.testsupport.fakes.StubOperatorTripOwnershipVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListTripBookingsServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final StubOperatorTripOwnershipVerifier ownershipVerifier = new StubOperatorTripOwnershipVerifier();
    private final ListTripBookingsService service = new ListTripBookingsService(bookingRepository, ownershipVerifier);

    @Test
    void deniesAnOperatorWhoDoesNotOwnTheTrip() {
        TripId tripId = new TripId(UUID.randomUUID());

        assertThatThrownBy(() -> service.list(
                new ListTripBookings.Command(tripId, new RequesterContext(UUID.randomUUID(), Role.OPERATOR))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allowsAnOperatorWhoOwnsTheTrip() {
        TripId tripId = new TripId(UUID.randomUUID());
        bookingRepository.save(Booking.create(BookingId.generate(), UUID.randomUUID(), tripId,
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0));
        ownershipVerifier.owns = true;

        ListTripBookings.Result result = service.list(
                new ListTripBookings.Command(tripId, new RequesterContext(UUID.randomUUID(), Role.OPERATOR)));

        assertThat(result.bookings()).hasSize(1);
    }

    @Test
    void alwaysAllowsAdmin() {
        TripId tripId = new TripId(UUID.randomUUID());

        ListTripBookings.Result result = service.list(
                new ListTripBookings.Command(tripId, new RequesterContext(UUID.randomUUID(), Role.ADMIN)));

        assertThat(result.bookings()).isEmpty();
    }
}
