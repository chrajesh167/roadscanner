package com.roadscanner.searchservice.location.domain.port.out;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link ProviderLocationMapping}.
 *
 * <p>The reverse lookups ({@link #findByProviderCityId}, {@link #findByProviderStationId}) are the
 * ones Sprint 3 needs to turn an inbound provider identifier back into a {@link LocationId} at the
 * integration boundary. They are declared now, and backed by the composite indexes in V2, so that
 * sprint adds a caller rather than a schema change.
 */
public interface ProviderLocationMappingRepository {

    Optional<ProviderLocationMapping> findByLocationAndProvider(LocationId locationId, ProviderCode provider);

    /** Every provider's view of one location. */
    List<ProviderLocationMapping> findByLocation(LocationId locationId);

    /** Resolve a provider's own city id back to a RoadScanner location. */
    Optional<ProviderLocationMapping> findByProviderCityId(ProviderCode provider, String providerCityId);

    /** Resolve a provider's own station id back to a RoadScanner location. */
    Optional<ProviderLocationMapping> findByProviderStationId(ProviderCode provider, String providerStationId);

    ProviderLocationMapping save(ProviderLocationMapping mapping);
}
