package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.ProviderBooking;
import com.roadscanner.providerintegrationservice.domain.model.ProviderBookingId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderBookingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Optional;

/** Translates between {@link ProviderBooking} and its row. */
@Repository
class ProviderBookingRepositoryAdapter implements ProviderBookingRepository {

    private final ProviderBookingSpringDataRepository repository;

    ProviderBookingRepositoryAdapter(ProviderBookingSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ProviderBooking save(ProviderBooking booking) {
        Optional<ProviderBookingJpaEntity> existing =
                repository.findById(booking.id().value());

        if (existing.isPresent()) {
            // Only the cancellation outcome moves after an order is recorded. The provider handles
            // were minted by the provider at confirmation and are never rewritten — overwriting
            // them with anything re-derived here is how a token is lost.
            ProviderBookingJpaEntity entity = existing.get();
            entity.recordCancellation(booking.cancelledAt().orElse(null), booking.refundedAmount().orElse(null));
            repository.save(entity);
            return booking;
        }

        repository.save(new ProviderBookingJpaEntity(
                booking.id().value(),
                booking.reservationId().value(),
                booking.providerType().code(),
                booking.bookingReference().value(),
                booking.providerCheckoutReference().orElse(null),
                booking.providerOrderReference(),
                booking.providerOrderToken().orElse(null),
                booking.totalFare().amount(),
                booking.totalFare().currency().getCurrencyCode(),
                booking.confirmedAt(),
                booking.cancelledAt().orElse(null),
                booking.refundedAmount().orElse(null)));
        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderBooking> findByOrderReference(ProviderType providerType, String providerOrderReference) {
        return repository.findByProviderTypeAndProviderOrderReference(providerType.code(), providerOrderReference)
                .map(ProviderBookingRepositoryAdapter::toDomain);
    }

    private static ProviderBooking toDomain(ProviderBookingJpaEntity entity) {
        return ProviderBooking.reconstitute(
                new ProviderBookingId(entity.id()),
                new ReservationId(entity.reservationId()),
                new ProviderType(entity.providerType()),
                new BookingReference(entity.bookingReference()),
                entity.providerCheckoutReference(),
                entity.providerOrderReference(),
                entity.providerOrderToken(),
                new FareAmount(entity.totalFareAmount(), Currency.getInstance(entity.totalFareCurrency())),
                entity.confirmedAt(),
                entity.cancelledAt(),
                entity.refundedAmount());
    }
}
