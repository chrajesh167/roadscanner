package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.TripId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Booking} — the platform's only source of truth for booking
 * records (docs/services/booking-service/data-ownership.md). */
public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(BookingId id);

    /** Every booking for a traveler, all statuses — backs {@code List Booking History} (FR-1.3). */
    List<Booking> findByTravelerId(UUID travelerId);

    /** Every booking against a trip, all statuses — backs {@code List Trip Bookings} (FR-5.5). */
    List<Booking> findByTripId(TripId tripId);

    /** Backs the cascade in {@code Handle Trip Cancelled} — only the non-terminal bookings need
     * to react to a trip cancellation. */
    List<Booking> findByTripIdAndStatusIn(TripId tripId, List<BookingStatus> statuses);

    /** Backs {@code Handle Seat Released} — a released reservation may reference a still-
     * {@code PENDING_PAYMENT} booking whose hold expired before payment was ever attempted. */
    Optional<Booking> findByProviderBlockReference(String providerBlockReference);

    /** Backs {@code Complete Booking}'s scheduled sweep. */
    List<Booking> findConfirmedWithDepartureBefore(Instant cutoff);

    /** Backs {@code Verify Booking} (FR-7.2, {@code review-service}'s only inbound
     * dependency on this service). */
    boolean existsCompletedByTravelerIdAndTripId(UUID travelerId, TripId tripId);

    /** Backs {@code Sweep Stale Holds}' defensive check for a {@code PENDING_PAYMENT} booking
     * whose hold expired with no {@code SeatReleased} ever arriving. */
    List<Booking> findPendingPaymentWithHoldExpiredBefore(Instant cutoff);
}
