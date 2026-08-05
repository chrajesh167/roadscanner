package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Implements {@link ProviderLocationMappingRepository} over Postgres via JPA. Package-private. */
@Repository
class ProviderLocationMappingRepositoryAdapter implements ProviderLocationMappingRepository {

    private final ProviderLocationMappingSpringDataRepository springDataRepository;
    private final ProviderLocationMappingMapper mapper = new ProviderLocationMappingMapper();

    ProviderLocationMappingRepositoryAdapter(ProviderLocationMappingSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<ProviderLocationMapping> findById(ProviderLocationMappingId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<ProviderLocationMapping> findByLocationAndProvider(LocationId locationId, ProviderCode provider) {
        return springDataRepository.findByLocationIdAndProvider(locationId.value(), provider.value())
                .map(mapper::toDomain);
    }

    @Override
    public ProviderLocationMappingRepository.Page search(Criteria criteria, int page, int size) {
        // A blank term and an absent term mean the same thing to the caller, and the query treats
        // null as "no filter" — so normalise here rather than making every caller remember to.
        String pattern = criteria.hasSearchTerm() ? toContainsPattern(criteria.searchTerm()) : null;
        String provider = criteria.provider() == null ? null : criteria.provider().value();

        org.springframework.data.domain.Page<ProviderLocationMappingJpaEntity> result =
                springDataRepository.search(provider, criteria.verified(), pattern, PageRequest.of(page, size));

        return new ProviderLocationMappingRepository.Page(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public void deleteById(ProviderLocationMappingId id) {
        // deleteById is already a no-op for a missing row in Spring Data, which is the idempotence
        // the port promises — a caller asking for this translation to not exist is satisfied
        // either way.
        springDataRepository.deleteById(id.value());
    }

    @Override
    public List<ProviderLocationMapping> findByLocation(LocationId locationId) {
        return springDataRepository.findByLocationId(locationId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderCityId(ProviderCode provider, String providerCityId) {
        return springDataRepository.findByProviderAndProviderCityId(provider.value(), providerCityId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderStationId(ProviderCode provider, String providerStationId) {
        return springDataRepository.findByProviderAndProviderStationId(provider.value(), providerStationId)
                .map(mapper::toDomain);
    }

    /**
     * Builds the {@code %term%} pattern the query expects, lower-cased once here rather than per
     * row. {@code \}, {@code %} and {@code _} are escaped so a term containing them matches
     * literally instead of turning into a wildcard — an operator pasting a provider id that
     * happens to contain an underscore should not silently match everything.
     */
    private static String toContainsPattern(String rawTerm) {
        String escaped = rawTerm.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    @Override
    public ProviderLocationMapping save(ProviderLocationMapping mapping) {
        ProviderLocationMappingJpaEntity entity = springDataRepository.findById(mapping.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, mapping);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(mapping));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
