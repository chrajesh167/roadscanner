package com.roadscanner.searchservice.location.domain.port.out;

import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;

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

    Location save(Location location);
}
