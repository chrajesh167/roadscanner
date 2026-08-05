package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
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
    public List<Location> findActiveWithoutMappingForProvider(ProviderCode provider, String searchTerm, int limit) {
        // Blank and absent mean the same thing to a caller; the query treats null as "no filter".
        // The pattern is built here rather than in JPQL because a null fed into lower() leaves
        // Postgres nothing to infer a type from and it binds as bytea — see the query's Javadoc.
        String pattern = searchTerm == null || searchTerm.isBlank()
                ? null
                : "%" + searchTerm.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";

        return springDataRepository
                .findActiveWithoutMappingForProvider(provider.value(), pattern, PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain)
                .toList();
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
