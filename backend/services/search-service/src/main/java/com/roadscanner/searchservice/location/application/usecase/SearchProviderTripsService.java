package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements {@link SearchProviderTrips}: discover, fan out, aggregate, isolate.
 *
 * <h2>Discovery</h2>
 * Candidates are the providers holding a city mapping for <em>both</em> endpoints. Asking a
 * provider that cannot express one end of the route wastes a call to learn nothing, and a
 * hard-coded provider list would have to be edited every time one is onboarded — the mappings
 * already carry that knowledge, so they are the source of truth.
 *
 * <h2>Isolation</h2>
 * Every provider is called inside its own guard. A timeout, a validation rejection, a rate limit
 * or an outright bug contributes nothing and is recorded, while the others still return results.
 * One provider having a bad day must never empty a traveller's search — that is the difference
 * between a degraded result and a broken product.
 *
 * <p>The guard catches {@code RuntimeException} deliberately, not a curated list. The point is to
 * contain <em>whatever</em> one provider's path can throw, including failures nobody anticipated;
 * a list would isolate only the failures already thought of, which are not the ones that take a
 * system down.
 *
 * <h2>Ordering</h2>
 * Aggregated results are sorted by departure time so the list is provider-blind. Concatenating in
 * call order would rank providers by how the fan-out happened to iterate, which is arbitrary and
 * would quietly favour whichever provider was mapped first.
 */
public class SearchProviderTripsService implements SearchProviderTrips {

    private static final Logger log = LoggerFactory.getLogger(SearchProviderTripsService.class);

    private final ProviderLocationMappingRepository mappingRepository;
    private final ProviderTripSearchClient searchClient;

    public SearchProviderTripsService(ProviderLocationMappingRepository mappingRepository,
                                      ProviderTripSearchClient searchClient) {
        this.mappingRepository = mappingRepository;
        this.searchClient = searchClient;
    }

    @Override
    public Result search(Command command) {
        Map<ProviderCode, String> origins = cityIdsByProvider(command.origin());
        Map<ProviderCode, String> destinations = cityIdsByProvider(command.destination());

        // Both ends, same provider. A provider mapped at only one end cannot express the route.
        Set<ProviderCode> candidates = origins.keySet().stream()
                .filter(destinations::containsKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (candidates.isEmpty()) {
            log.debug("No provider has a city mapping for both endpoints — no provider search performed");
            return new Result(List.of(), Set.of(), Set.of(), Set.of());
        }

        List<ProviderTripResult> aggregated = new ArrayList<>();
        Set<ProviderCode> succeeded = new LinkedHashSet<>();
        Set<ProviderCode> failed = new LinkedHashSet<>();

        for (ProviderCode provider : candidates) {
            try {
                aggregated.addAll(searchClient.search(provider, origins.get(provider),
                        destinations.get(provider), command.travelDate()));
                succeeded.add(provider);
            } catch (RuntimeException e) {
                // Contained here so the remaining providers still run. Recorded, never silent:
                // a partial result presented as complete is worse than a visibly partial one.
                failed.add(provider);
                log.warn("Provider {} failed during federated search — continuing with the others",
                        provider, e);
            }
        }

        aggregated.sort(Comparator.comparing(ProviderTripResult::departureTime));
        return new Result(aggregated, candidates, succeeded, failed);
    }

    /**
     * Every provider that can name this location by city id, keyed by provider.
     *
     * <p>A station-only mapping is excluded: search needs a city id specifically, and substituting
     * a station id would send a provider something that is not the thing it asked for.
     */
    private Map<ProviderCode, String> cityIdsByProvider(LocationId locationId) {
        return mappingRepository.findByLocation(locationId).stream()
                .filter(mapping -> mapping.placeRef().cityId() != null)
                .collect(Collectors.toMap(ProviderLocationMapping::provider,
                        mapping -> mapping.placeRef().cityId(),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }
}
