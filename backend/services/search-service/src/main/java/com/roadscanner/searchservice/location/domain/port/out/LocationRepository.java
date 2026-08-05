package com.roadscanner.searchservice.location.domain.port.out;

import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link Location}. Implemented by a Postgres/JPA adapter in
 * {@code location.adapter.out.persistence}; this package depends on nothing outside the domain
 * layer, the same dependency rule {@code SearchableTripRepository} enforces.
 *
 * <p>Returns domain types only — never a Spring Data {@code Page}, {@code Specification} or
 * entity. Those are adapter-internal and are translated at the boundary.
 */
public interface LocationRepository {

    Optional<Location> findById(LocationId id);

    /**
     * Prefix search over display name and city, for autocomplete.
     *
     * <p>Inactive locations are excluded by the adapter: a soft-deleted place must stop being
     * <em>offered</em> while remaining resolvable by id. Keeping that rule in the adapter's query
     * rather than filtering in memory means a large catalogue never loads rows it will discard.
     *
     * @param prefix caller-supplied fragment, matched case-insensitively
     * @param limit  maximum rows to return; the caller has already clamped this
     */
    List<Location> searchActiveByPrefix(String prefix, int limit);

    /** Used to enforce the optional-but-unique rule on Google place ids before writing. */
    Optional<Location> findByGooglePlaceId(GooglePlaceId googlePlaceId);

    /**
     * Active locations that have no mapping for the given provider — the onboarding worklist.
     *
     * <p>Answered with a single anti-join rather than by loading every location and every mapping
     * and subtracting in memory, which would grow with the product of two tables to render one
     * page.
     *
     * <p>Lives on this port rather than on {@code ProviderLocationMappingRepository} because it
     * returns {@link Location} aggregates; the provider is a filter on the answer, not the subject
     * of it. Taking a {@link ProviderCode} here couples nothing new — both types already belong to
     * this module's domain.
     *
     * <p>Inactive locations are excluded for the same reason autocomplete excludes them: a
     * withdrawn place should not be offered as work to do.
     *
     * @param provider   the provider whose mappings are missing
     * @param searchTerm optional case-insensitive substring over display name and city
     * @param limit      maximum rows; the caller has already clamped this
     */
    List<Location> findActiveWithoutMappingForProvider(ProviderCode provider, String searchTerm, int limit);

    Location save(Location location);
}
