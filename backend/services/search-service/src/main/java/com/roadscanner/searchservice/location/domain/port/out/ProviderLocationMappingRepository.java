package com.roadscanner.searchservice.location.domain.port.out;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link ProviderLocationMapping}.
 *
 * <p>The reverse lookups ({@link #findByProviderCityId}, {@link #findByProviderStationId}) turn an
 * inbound provider identifier back into a {@link LocationId} at the integration boundary. V5's
 * unique indexes are what make their {@code Optional} return type honest — before V5 nothing
 * prevented two rows sharing a provider id, so "the mapping" was whichever row the planner
 * happened to reach first.
 *
 * <p>The administrative operations ({@link #findById}, {@link #search}, {@link #deleteById}) back
 * the Sprint 5B admin API. They return domain types and a plain {@link Page} record — never a
 * Spring Data {@code Page}, {@code Specification} or entity, which stay adapter-internal, the same
 * rule the rest of this module's ports keep.
 */
public interface ProviderLocationMappingRepository {

    Optional<ProviderLocationMapping> findById(ProviderLocationMappingId id);

    Optional<ProviderLocationMapping> findByLocationAndProvider(LocationId locationId, ProviderCode provider);

    /** Every provider's view of one location. */
    List<ProviderLocationMapping> findByLocation(LocationId locationId);

    /** Resolve a provider's own city id back to a RoadScanner location. */
    Optional<ProviderLocationMapping> findByProviderCityId(ProviderCode provider, String providerCityId);

    /** Resolve a provider's own station id back to a RoadScanner location. */
    Optional<ProviderLocationMapping> findByProviderStationId(ProviderCode provider, String providerStationId);

    /**
     * Paged administrative search.
     *
     * <p>Filters combine with AND and every one is optional — an empty {@link Criteria} lists the
     * whole table. The free-text term spans the canonical location's display name and city as well
     * as the three provider fields, because an administrator reconciling a mapping holds whichever
     * of those the provider's own console showed them and should not have to know which column it
     * lives in.
     */
    Page search(Criteria criteria, int page, int size);

    /**
     * Removes a mapping outright.
     *
     * <p>A hard delete, unlike {@code Location}'s soft delete, and the difference is deliberate: a
     * location is master data that historical trips and bookings still refer to, whereas a mapping
     * is a translation rule with nothing pointing at it. A wrong translation needs to stop
     * existing — keeping it as an inactive row would leave something every future reader has to
     * remember to filter out, and would keep occupying the unique keys V5 enforces.
     */
    void deleteById(ProviderLocationMappingId id);

    ProviderLocationMapping save(ProviderLocationMapping mapping);

    /**
     * @param provider   restrict to one provider's mappings
     * @param verified   restrict to confirmed or unconfirmed mappings
     * @param searchTerm case-insensitive substring over location display name and city, provider
     *                   city id, provider station id and provider station name
     */
    record Criteria(ProviderCode provider, Boolean verified, String searchTerm) {

        public static Criteria unfiltered() {
            return new Criteria(null, null, null);
        }

        public boolean hasSearchTerm() {
            return searchTerm != null && !searchTerm.isBlank();
        }
    }

    /** A page of results plus the total, so a caller can render "x of y" without a second count
     * query it would then have to keep consistent with this one. */
    record Page(List<ProviderLocationMapping> content, long totalElements, int page, int size) {

        public Page {
            content = List.copyOf(content);
        }

        public int totalPages() {
            return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        }
    }
}
