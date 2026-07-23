package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryBookingRepository implements BookingRepository {

    private final Map<UUID, Booking> bookings = new LinkedHashMap<>();

    @Override
    public Booking save(Booking booking) {
        bookings.put(booking.id().value(), booking);
        return booking;
    }

    @Override
    public Optional<Booking> findById(BookingId id) {
        return Optional.ofNullable(bookings.get(id.value()));
    }

    @Override
    public List<Booking> findByTravelerId(UUID travelerId) {
        return bookings.values().stream().filter(b -> b.travelerId().equals(travelerId)).toList();
    }

    @Override
    public List<Booking> findByTripId(TripId tripId) {
        return bookings.values().stream().filter(b -> b.tripId().equals(tripId)).toList();
    }

    @Override
    public List<Booking> findByTripIdAndStatusIn(TripId tripId, List<BookingStatus> statuses) {
        return bookings.values().stream()
                .filter(b -> b.tripId().equals(tripId) && statuses.contains(b.status()))
                .toList();
    }

    @Override
    public Optional<Booking> findByProviderBlockReference(String providerBlockReference) {
        return bookings.values().stream()
                .filter(b -> b.providerBlockReference().equals(providerBlockReference))
                .findFirst();
    }

    @Override
    public List<Booking> findConfirmedWithDepartureBefore(Instant cutoff) {
        return bookings.values().stream()
                .filter(b -> b.status() == BookingStatus.CONFIRMED && b.tripDepartureTime().isBefore(cutoff))
                .toList();
    }

    @Override
    public boolean existsCompletedByTravelerIdAndTripId(UUID travelerId, TripId tripId) {
        return bookings.values().stream()
                .anyMatch(b -> b.travelerId().equals(travelerId) && b.tripId().equals(tripId)
                        && b.status() == BookingStatus.COMPLETED);
    }

    @Override
    public List<Booking> findPendingPaymentWithHoldExpiredBefore(Instant cutoff) {
        return bookings.values().stream()
                .filter(b -> b.status() == BookingStatus.PENDING_PAYMENT && b.holdExpiresAt().isBefore(cutoff))
                .toList();
    }
}
