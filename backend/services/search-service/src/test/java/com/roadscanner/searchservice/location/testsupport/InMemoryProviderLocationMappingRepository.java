package com.roadscanner.searchservice.location.testsupport;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory {@link ProviderLocationMappingRepository} for application-layer tests. */
public final class InMemoryProviderLocationMappingRepository implements ProviderLocationMappingRepository {

    private final Map<ProviderLocationMappingId, ProviderLocationMapping> stored = new LinkedHashMap<>();

    @Override
    public Optional<ProviderLocationMapping> findById(ProviderLocationMappingId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<ProviderLocationMapping> findByLocationAndProvider(LocationId locationId, ProviderCode provider) {
        return stored.values().stream()
                .filter(mapping -> mapping.locationId().equals(locationId) && mapping.provider().equals(provider))
                .findFirst();
    }

    /**
     * Applies the same filters as the real query, in insertion order.
     *
     * <p>The search term deliberately covers only the three provider fields here, not the location's
     * display name or city: this double holds no locations to join against. Tests that exercise the
     * cross-table half of the search use the Testcontainers-backed adapter test, where the join is
     * real — a fake that pretended to search a table it cannot see would prove nothing.
     */
    @Override
    public Page search(Criteria criteria, int page, int size) {
        List<ProviderLocationMapping> matches = stored.values().stream()
                .filter(mapping -> criteria.provider() == null || mapping.provider().equals(criteria.provider()))
                .filter(mapping -> criteria.verified() == null || mapping.isVerified() == criteria.verified())
                .filter(mapping -> !criteria.hasSearchTerm() || matchesTerm(mapping, criteria.searchTerm()))
                .toList();

        int from = Math.min(page * size, matches.size());
        int to = Math.min(from + size, matches.size());
        return new Page(matches.subList(from, to), matches.size(), page, size);
    }

    private static boolean matchesTerm(ProviderLocationMapping mapping, String term) {
        String needle = term.trim().toLowerCase(java.util.Locale.ROOT);
        return contains(mapping.placeRef().cityId(), needle)
                || contains(mapping.placeRef().stationId(), needle)
                || contains(mapping.placeRef().stationName(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    @Override
    public void deleteById(ProviderLocationMappingId id) {
        stored.remove(id);
    }

    @Override
    public List<ProviderLocationMapping> findByLocation(LocationId locationId) {
        return stored.values().stream()
                .filter(mapping -> mapping.locationId().equals(locationId))
                .toList();
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderCityId(ProviderCode provider, String providerCityId) {
        return stored.values().stream()
                .filter(mapping -> mapping.provider().equals(provider)
                        && Objects.equals(mapping.placeRef().cityId(), providerCityId))
                .findFirst();
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderStationId(ProviderCode provider, String providerStationId) {
        return stored.values().stream()
                .filter(mapping -> mapping.provider().equals(provider)
                        && Objects.equals(mapping.placeRef().stationId(), providerStationId))
                .findFirst();
    }

    @Override
    public ProviderLocationMapping save(ProviderLocationMapping mapping) {
        stored.put(mapping.id(), mapping);
        return mapping;
    }

    public void seed(ProviderLocationMapping... mappings) {
        for (ProviderLocationMapping mapping : mappings) {
            stored.put(mapping.id(), mapping);
        }
    }
}
