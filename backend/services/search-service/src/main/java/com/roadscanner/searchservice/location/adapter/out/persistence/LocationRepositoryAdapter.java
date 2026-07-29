package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implements the {@link LocationRepository} domain port over Postgres via JPA. Package-private —
 * consumers depend on the port interface only.
 *
 * <p>{@link #save} fetches-then-mutates for an existing row rather than always constructing a
 * fresh entity, matching {@code SearchableTripRepositoryAdapter}: handing Hibernate a detached
 * entity built from scratch would turn every update into a blind overwrite of the whole row.
 */
@Repository
class LocationRepositoryAdapter implements LocationRepository {

    private final LocationSpringDataRepository springDataRepository;
    private final LocationMapper mapper = new LocationMapper();

    LocationRepositoryAdapter(LocationSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Location> findById(LocationId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Location> searchActiveByPrefix(String prefix, int limit) {
        return springDataRepository.searchActiveByPrefix(prefix, PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Location> findByGooglePlaceId(GooglePlaceId googlePlaceId) {
        return springDataRepository.findByGooglePlaceId(googlePlaceId.value()).map(mapper::toDomain);
    }

    @Override
    public Location save(Location location) {
        LocationJpaEntity entity = springDataRepository.findById(location.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, location);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(location));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
