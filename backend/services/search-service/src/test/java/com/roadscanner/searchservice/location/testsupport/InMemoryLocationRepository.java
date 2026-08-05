package com.roadscanner.searchservice.location.testsupport;

import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link LocationRepository} for application-layer tests.
 *
 * <p>Reproduces the adapter's <em>contract</em> — active-only filtering, prefix match on display
 * name or city, display-name matches ranked first, limit applied — but not its SQL. The real
 * query semantics (collation, index use, ordering under Postgres) are covered by
 * {@code LocationRepositoryAdapterTest} against a real database, exactly as
 * {@code InMemorySearchableTripRepository} defers the filter/sort combinatorics to its
 * Testcontainers counterpart.
 */
public final class InMemoryLocationRepository implements LocationRepository {

    private final Map<LocationId, Location> stored = new LinkedHashMap<>();

    /** Optional; set via {@link #linkMappings}. Null means "no mappings exist". */
    private InMemoryProviderLocationMappingRepository mappings;

    @Override
    public Optional<Location> findById(LocationId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public List<Location> searchActiveByPrefix(String prefix, int limit) {
        String needle = prefix.toLowerCase(Locale.ROOT);

        List<Location> matches = new ArrayList<>(stored.values().stream()
                .filter(Location::isActive)
                .filter(location -> startsWith(location.displayName(), needle)
                        || startsWith(location.address().city(), needle))
                .toList());

        matches.sort(Comparator
                .comparingInt((Location location) -> startsWith(location.displayName(), needle) ? 0 : 1)
                .thenComparing(Location::displayName));

        return matches.size() > limit ? List.copyOf(matches.subList(0, limit)) : List.copyOf(matches);
    }

    @Override
    public Optional<Location> findByGooglePlaceId(GooglePlaceId googlePlaceId) {
        return stored.values().stream()
                .filter(location -> location.googlePlaceId().filter(googlePlaceId::equals).isPresent())
                .findFirst();
    }

    /**
     * Reproduces the anti-join's contract by consulting the mapping double this one has been
     * linked to. Unlinked, every active location counts as unmapped — which is the correct answer
     * for a fixture that holds no mappings, not a shortcut.
     *
     * <p>Whether Postgres actually turns the {@code NOT EXISTS} into an anti-join, and whether it
     * uses the unique index, is the adapter test's business.
     */
    @Override
    public List<Location> findActiveWithoutMappingForProvider(ProviderCode provider, String searchTerm, int limit) {
        String needle = searchTerm == null || searchTerm.isBlank()
                ? null
                : searchTerm.trim().toLowerCase(Locale.ROOT);

        return stored.values().stream()
                .filter(Location::isActive)
                .filter(location -> mappings == null
                        || mappings.findByLocationAndProvider(location.id(), provider).isEmpty())
                .filter(location -> needle == null
                        || contains(location.displayName(), needle)
                        || contains(location.address().city(), needle))
                .sorted(Comparator.comparing(Location::displayName))
                .limit(limit)
                .toList();
    }

    /** Links the mapping double so the anti-join above has something to exclude against. */
    public InMemoryLocationRepository linkMappings(InMemoryProviderLocationMappingRepository mappingRepository) {
        this.mappings = mappingRepository;
        return this;
    }

    @Override
    public Location save(Location location) {
        stored.put(location.id(), location);
        return location;
    }

    /** Seeds without going through save(), for arranging test state. */
    public void seed(Location... locations) {
        for (Location location : locations) {
            stored.put(location.id(), location);
        }
    }

    public int count() {
        return stored.size();
    }

    private static boolean startsWith(String value, String lowerCasePrefix) {
        return value.toLowerCase(Locale.ROOT).startsWith(lowerCasePrefix);
    }

    private static boolean contains(String value, String lowerCaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }
}
