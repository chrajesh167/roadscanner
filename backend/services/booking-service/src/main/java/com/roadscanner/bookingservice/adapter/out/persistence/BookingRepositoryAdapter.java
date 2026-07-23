package com.roadscanner.bookingservice.adapter.out.persistence;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fetches-then-mutates on {@link #save}, matching {@code inventory-service}'s
 * {@code TripRepositoryAdapter} optimistic-locking rationale: a fresh-entity save would hand
 * Hibernate no {@code @Version} read from the database, bypassing the check that guards two
 * concurrently-processed triggers for the same booking (e.g. a redelivered {@code PaymentCompleted}
 * racing a traveler-initiated cancellation) from clobbering each other
 * (docs/services/booking-service/domain-model.md's "Concurrency"). */
@Repository
class BookingRepositoryAdapter implements BookingRepository {

    private final BookingSpringDataRepository springDataRepository;
    private final BookingMapper mapper = new BookingMapper();

    BookingRepositoryAdapter(BookingSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Booking save(Booking booking) {
        BookingJpaEntity entity = springDataRepository.findById(booking.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, booking);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(booking));
        return mapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Booking> findById(BookingId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Booking> findByTravelerId(UUID travelerId) {
        return springDataRepository.findByTravelerId(travelerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Booking> findByTripId(TripId tripId) {
        return springDataRepository.findByTripId(tripId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Booking> findByTripIdAndStatusIn(TripId tripId, List<BookingStatus> statuses) {
        List<String> statusNames = statuses.stream().map(Enum::name).toList();
        return springDataRepository.findByTripIdAndStatusIn(tripId.value(), statusNames).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Booking> findByProviderBlockReference(String providerBlockReference) {
        return springDataRepository.findByProviderBlockReference(providerBlockReference).map(mapper::toDomain);
    }

    @Override
    public List<Booking> findConfirmedWithDepartureBefore(Instant cutoff) {
        return springDataRepository.findByStatusAndTripDepartureTimeBefore(BookingStatus.CONFIRMED.name(), cutoff)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsCompletedByTravelerIdAndTripId(UUID travelerId, TripId tripId) {
        return springDataRepository.existsByTravelerIdAndTripIdAndStatus(travelerId, tripId.value(),
                BookingStatus.COMPLETED.name());
    }

    @Override
    public List<Booking> findPendingPaymentWithHoldExpiredBefore(Instant cutoff) {
        return springDataRepository.findByStatusAndHoldExpiresAtBefore(BookingStatus.PENDING_PAYMENT.name(), cutoff)
                .stream().map(mapper::toDomain).toList();
    }
}
