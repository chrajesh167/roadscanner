package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import com.roadscanner.providerintegrationservice.domain.model.ReservationStatus;
import com.roadscanner.providerintegrationservice.domain.model.SeatAssignment;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderReservationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Translates between {@link SeatReservation} and its rows. */
@Repository
class ProviderReservationRepositoryAdapter implements ProviderReservationRepository {

    private final ProviderReservationSpringDataRepository repository;

    ProviderReservationRepositoryAdapter(ProviderReservationSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public SeatReservation save(SeatReservation reservation) {
        Optional<ProviderReservationJpaEntity> existing = repository.findById(reservation.reservationId().value());

        if (existing.isPresent()) {
            // Only the status moves after a hold is placed. The seat rows are written once, at
            // block time, and are never rewritten: the provider handles they carry were minted by
            // the provider and re-deriving them here would either duplicate the rows or, worse,
            // replace real provider ids with freshly generated ones.
            ProviderReservationJpaEntity entity = existing.get();
            entity.updateStatus(reservation.status().name());
            repository.save(entity);
            return reservation;
        }

        ProviderReservationJpaEntity entity = new ProviderReservationJpaEntity(
                reservation.reservationId().value(),
                reservation.providerType().code(),
                reservation.providerBlockReference(),
                reservation.providerTripId(),
                reservation.status().name(),
                reservation.blockedAt(),
                reservation.expiresAt());
        entity.replaceSeats(toSeatEntities(reservation.seatAssignments()));

        repository.save(entity);
        return reservation;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeatReservation> findById(ReservationId reservationId) {
        return repository.findById(reservationId.value()).map(ProviderReservationRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeatReservation> findByBlockReference(ProviderType providerType, String providerBlockReference) {
        return repository.findByProviderTypeAndProviderBlockReference(providerType.code(), providerBlockReference)
                .map(ProviderReservationRepositoryAdapter::toDomain);
    }

    private static List<ProviderReservationSeatJpaEntity> toSeatEntities(List<SeatAssignment> assignments) {
        return java.util.stream.IntStream.range(0, assignments.size())
                .mapToObj(i -> {
                    SeatAssignment assignment = assignments.get(i);
                    return new ProviderReservationSeatJpaEntity(UUID.randomUUID(), assignment.seatNumber().value(),
                            assignment.providerSeatId(), assignment.providerTicketId(), i);
                })
                .toList();
    }

    private static SeatReservation toDomain(ProviderReservationJpaEntity entity) {
        List<SeatAssignment> assignments = entity.seats().stream()
                .map(seat -> new SeatAssignment(new SeatNumber(seat.seatNumber()), seat.providerSeatId(),
                        seat.providerTicketId()))
                .toList();

        return SeatReservation.reconstitute(new ReservationId(entity.id()), new ProviderType(entity.providerType()),
                entity.providerBlockReference(), entity.providerTripId(), assignments,
                ReservationStatus.valueOf(entity.status()), entity.blockedAt(), entity.expiresAt());
    }
}
