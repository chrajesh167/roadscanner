package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fetches-then-mutates on {@link #save}, matching {@code SearchableTripRepositoryAdapter}'s
 * optimistic-locking rationale: a fresh-entity save would hand Hibernate no {@code @Version}
 * read from the database, bypassing the check that guards two concurrently-processed events for
 * the same trip (e.g. a redelivered {@code TripUpdated} racing a catalog-sync reconciliation)
 * from clobbering each other. */
@Repository
class TripRepositoryAdapter implements TripRepository {

    private final TripSpringDataRepository springDataRepository;
    private final TripMapper mapper = new TripMapper();

    TripRepositoryAdapter(TripSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Trip> findById(TripId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Trip save(Trip trip) {
        TripJpaEntity entity = springDataRepository.findById(trip.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, trip);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(trip));
        return mapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public List<Trip> findByOperatorId(UUID operatorId) {
        return springDataRepository.findByOperatorId(operatorId).stream().map(mapper::toDomain).toList();
    }
}
