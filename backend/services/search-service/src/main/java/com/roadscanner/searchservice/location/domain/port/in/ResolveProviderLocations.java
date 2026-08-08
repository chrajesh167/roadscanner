package com.roadscanner.searchservice.location.domain.port.in;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Translates canonical RoadScanner locations into one provider's own city ids.
 *
 * <p>This exists because {@code provider_location_mapping} is the platform's single translation
 * table and lives here, while the caller that most needs it — {@code inventory-service}'s catalog
 * sync — is a different service and cannot read this database. Sync was sending city <em>names</em>
 * where providers require the ids they issued, so no trip could ever be synchronised from a real
 * provider. Exposing the existing lookup is what closes that, without a second mapping table
 * anywhere on the platform.
 *
 * <p>Read-only and deliberately narrow: it answers exactly "what does this provider call these
 * places", and nothing else. The administrative surface over the same table is unchanged and stays
 * where it is.
 */
public interface ResolveProviderLocations {

    Result resolve(Query query);

    record Query(ProviderCode provider, List<LocationId> locationIds) {

        public Query {
            Objects.requireNonNull(provider, "provider must not be null");
            if (locationIds == null || locationIds.isEmpty()) {
                throw new IllegalArgumentException("locationIds must not be empty");
            }
            locationIds = List.copyOf(locationIds);
        }
    }

    /**
     * @param cityIdsByLocation the provider's city id for every location it can name
     * @param unresolved        the locations this provider has no usable mapping for, listed rather
     *                          than silently dropped — a caller searching a route must be able to
     *                          tell "this provider does not serve this city" from "the mapping is
     *                          missing", and both differ from an empty result
     */
    record Result(Map<LocationId, String> cityIdsByLocation, List<LocationId> unresolved) {

        public Result {
            cityIdsByLocation = Map.copyOf(cityIdsByLocation);
            unresolved = List.copyOf(unresolved);
        }
    }
}
