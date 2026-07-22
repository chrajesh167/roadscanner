package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.SeatLayout;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.out.SeatLayoutRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class SeatLayoutRepositoryAdapter implements SeatLayoutRepository {

    private final SeatLayoutSpringDataRepository springDataRepository;
    private final SeatLayoutMapper mapper = new SeatLayoutMapper();

    SeatLayoutRepositoryAdapter(SeatLayoutSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<SeatLayout> findByTripId(TripId tripId) {
        return springDataRepository.findById(tripId.value()).map(mapper::toDomain);
    }

    @Override
    public SeatLayout save(SeatLayout seatLayout) {
        // Seat layouts are materialized once and effectively immutable (Seat's Javadoc) — always
        // a fresh insert-or-replace, never a fetch-then-mutate; there is no concurrent-update
        // race to guard against for data that's written exactly once per trip.
        return mapper.toDomain(springDataRepository.save(mapper.toNewEntity(seatLayout)));
    }
}
