package com.roadscanner.bookingservice.adapter.out.persistence;

import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Always fresh-inserts on {@link #save} — {@code SeatHold} is never mutated once created, only
 * created or deleted (docs/services/booking-service/data-ownership.md). */
@Repository
class SeatHoldRepositoryAdapter implements SeatHoldRepository {

    private final SeatHoldSpringDataRepository springDataRepository;
    private final SeatHoldMapper mapper = new SeatHoldMapper();

    SeatHoldRepositoryAdapter(SeatHoldSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public SeatHold save(SeatHold seatHold) {
        return mapper.toDomain(springDataRepository.save(mapper.toNewEntity(seatHold)));
    }

    @Override
    public Optional<SeatHold> findById(SeatHoldId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<SeatHold> findByProviderBlockReference(String providerBlockReference) {
        return springDataRepository.findByProviderBlockReference(providerBlockReference).map(mapper::toDomain);
    }

    @Override
    public void deleteById(SeatHoldId id) {
        springDataRepository.deleteById(id.value());
    }

    @Override
    public List<SeatHold> findAllExpiredBefore(Instant cutoff) {
        return springDataRepository.findByExpiresAtBefore(cutoff).stream().map(mapper::toDomain).toList();
    }
}
