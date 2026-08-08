package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.port.in.ResolveProviderLocations;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implements {@link ResolveProviderLocations} over the same
 * {@link ProviderLocationMappingRepository} every other reader of the mapping table uses. No new
 * query and no second copy of the translation rule — the lookup already existed
 * ({@code findByLocationAndProvider}); only a caller outside this service could not reach it.
 *
 * <p>Two conditions must hold for a mapping to be usable here, and both fail closed:
 *
 * <ul>
 *   <li><strong>It must carry a city id.</strong> A station-only mapping is not a substitute:
 *       providers search by city, and sending a station id would hand them something that is not
 *       the thing they were asked for. This matches what the live federation already requires.
 *   <li><strong>It must be verified.</strong> Stricter than the federation, on purpose. An
 *       unverified mapping is a claim nobody has confirmed; acting on one in <em>search</em> shows
 *       a questionable result a traveller can ignore, but acting on one <em>here</em> imports a
 *       trip into the catalog as bookable, and a wrong city id books a real seat on a real bus in
 *       the wrong place. The failure is discovered at boarding, which is far too late.
 * </ul>
 *
 * <p>A location with no usable mapping is reported as unresolved rather than guessed at. Nothing in
 * this class matches on names: a mapping that is merely plausible is exactly the failure
 * {@code V4__seed_flixbus_location_mappings.sql} refuses to create by name-matching, and it would
 * be no safer to invent one at read time.
 */
public class ResolveProviderLocationsService implements ResolveProviderLocations {

    private final ProviderLocationMappingRepository mappingRepository;

    public ResolveProviderLocationsService(ProviderLocationMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @Override
    public Result resolve(Query query) {
        Map<LocationId, String> resolved = new LinkedHashMap<>();
        List<LocationId> unresolved = new ArrayList<>();

        for (LocationId locationId : query.locationIds()) {
            Optional<String> cityId = mappingRepository
                    .findByLocationAndProvider(locationId, query.provider())
                    .filter(ProviderLocationMapping::isVerified)
                    .map(mapping -> mapping.placeRef().cityId())
                    .filter(id -> id != null && !id.isBlank());

            if (cityId.isPresent()) {
                resolved.put(locationId, cityId.get());
            } else {
                unresolved.add(locationId);
            }
        }

        return new Result(resolved, unresolved);
    }
}
