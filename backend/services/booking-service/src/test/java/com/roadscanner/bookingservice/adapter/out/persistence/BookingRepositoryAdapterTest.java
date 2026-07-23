package com.roadscanner.bookingservice.adapter.out.persistence;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/** Exercises {@link BookingRepositoryAdapter} against a real Postgres (Testcontainers) — in
 * particular the fetch-then-mutate {@code save} path (optimistic locking) and every derived
 * query the application layer's in-memory fake cannot prove against the real schema/constraints. */
@DataJpaTest
@Import({TestcontainersConfiguration.class, BookingRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class BookingRepositoryAdapterTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    private BookingRepositoryAdapter adapter;

    private Booking newBooking(UUID travelerId, TripId tripId, String providerBlockReference,
                                Instant tripDepartureTime) {
        return Booking.create(BookingId.generate(), travelerId, tripId, tripDepartureTime, new ProviderType("MOCK"),
                "MOCK-TRIP-1", providerBlockReference, T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
    }

    @Test
    void savesAndRoundTripsANewBooking() {
        Booking booking = newBooking(UUID.randomUUID(), new TripId(UUID.randomUUID()), "block-ref-1",
                T0.plusSeconds(3600));

        adapter.save(booking);

        Booking found = adapter.findById(booking.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(found.passengers()).hasSize(1);
        assertThat(found.fare().amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void savingAnExistingBookingUpdatesInPlaceRatherThanInserting() {
        Booking booking = newBooking(UUID.randomUUID(), new TripId(UUID.randomUUID()), "block-ref-2",
                T0.plusSeconds(3600));
        adapter.save(booking);

        Booking reloaded = adapter.findById(booking.id()).orElseThrow();
        reloaded.confirm("provider-booking-ref-1", new Ticket("t1", "PDF", "content".getBytes(), T0.plusSeconds(10)),
                T0.plusSeconds(10));
        adapter.save(reloaded);

        Booking found = adapter.findById(booking.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(found.ticket()).isPresent();
        assertThat(found.ticket().get().content()).isEqualTo("content".getBytes());
    }

    @Test
    void findByTravelerIdReturnsEveryBookingRegardlessOfStatus() {
        UUID travelerId = UUID.randomUUID();
        Booking pending = newBooking(travelerId, new TripId(UUID.randomUUID()), "block-ref-3", T0.plusSeconds(3600));
        Booking cancelled = newBooking(travelerId, new TripId(UUID.randomUUID()), "block-ref-4", T0.plusSeconds(3600));
        cancelled.cancel(CancellationReason.TRAVELER_REQUESTED, T0.plusSeconds(20));
        adapter.save(pending);
        adapter.save(cancelled);

        List<Booking> found = adapter.findByTravelerId(travelerId);

        assertThat(found).extracting(Booking::id).containsExactlyInAnyOrder(pending.id(), cancelled.id());
    }

    @Test
    void findByTripIdAndStatusInFiltersCorrectly() {
        TripId tripId = new TripId(UUID.randomUUID());
        Booking pending = newBooking(UUID.randomUUID(), tripId, "block-ref-5", T0.plusSeconds(3600));
        Booking confirmed = newBooking(UUID.randomUUID(), tripId, "block-ref-6", T0.plusSeconds(3600));
        confirmed.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        Booking cancelled = newBooking(UUID.randomUUID(), tripId, "block-ref-7", T0.plusSeconds(3600));
        cancelled.cancel(CancellationReason.TRAVELER_REQUESTED, T0.plusSeconds(10));
        adapter.save(pending);
        adapter.save(confirmed);
        adapter.save(cancelled);

        List<Booking> found = adapter.findByTripIdAndStatusIn(tripId,
                List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));

        assertThat(found).extracting(Booking::id).containsExactlyInAnyOrder(pending.id(), confirmed.id());
    }

    @Test
    void findByProviderBlockReferenceLocatesTheBooking() {
        Booking booking = newBooking(UUID.randomUUID(), new TripId(UUID.randomUUID()), "block-ref-8",
                T0.plusSeconds(3600));
        adapter.save(booking);

        assertThat(adapter.findByProviderBlockReference("block-ref-8")).isPresent();
        assertThat(adapter.findByProviderBlockReference("no-such-ref")).isEmpty();
    }

    @Test
    void findConfirmedWithDepartureBeforeOnlyReturnsConfirmedDepartedTrips() {
        Booking departed = newBooking(UUID.randomUUID(), new TripId(UUID.randomUUID()), "block-ref-9",
                T0.minusSeconds(3600));
        departed.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        Booking notDeparted = newBooking(UUID.randomUUID(), new TripId(UUID.randomUUID()), "block-ref-10",
                T0.plusSeconds(3600));
        notDeparted.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        adapter.save(departed);
        adapter.save(notDeparted);

        List<Booking> found = adapter.findConfirmedWithDepartureBefore(T0);

        assertThat(found).extracting(Booking::id).containsExactly(departed.id());
    }

    @Test
    void existsCompletedByTravelerIdAndTripIdBacksVerifyBooking() {
        UUID travelerId = UUID.randomUUID();
        TripId tripId = new TripId(UUID.randomUUID());
        Booking booking = newBooking(travelerId, tripId, "block-ref-11", T0.plusSeconds(3600));
        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        booking.complete(T0.plusSeconds(9999));
        adapter.save(booking);

        assertThat(adapter.existsCompletedByTravelerIdAndTripId(travelerId, tripId)).isTrue();
        assertThat(adapter.existsCompletedByTravelerIdAndTripId(UUID.randomUUID(), tripId)).isFalse();
    }

    @Test
    void findPendingPaymentWithHoldExpiredBeforeBacksTheSweep() {
        Booking stale = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-12", T0.minusSeconds(100),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0.minusSeconds(700));
        Booking fresh = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-13", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        adapter.save(stale);
        adapter.save(fresh);

        List<Booking> found = adapter.findPendingPaymentWithHoldExpiredBefore(T0);

        assertThat(found).extracting(Booking::id).containsExactly(stale.id());
    }
}
