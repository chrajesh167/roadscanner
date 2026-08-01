package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Implements {@link SearchProviderTrips}: canonical location in, normalized trips out.
 *
 * <p>This is the translation seam Sprint 1 built {@code provider_location_mapping} for. Both
 * endpoints are resolved through it before any provider call is made.
 *
 * <p><strong>Never guesses and never falls back.</strong> If either location has no mapping for
 * this provider, the search is not attempted and the result reports {@code mapped=false}. Sending
 * a guessed or defaulted id would ask the provider about a place we cannot identify and present
 * whatever came back as though it were the requested route — silently wrong, and impossible to
 * spot from the outside. "This provider does not serve that route" is the correct business answer.
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
        Optional<String> originCityId = cityIdFor(command.provider(), command.origin());
        Optional<String> destinationCityId = cityIdFor(command.provider(), command.destination());

        if (originCityId.isEmpty() || destinationCityId.isEmpty()) {
            log.debug("Provider {} has no mapping for one or both endpoints — skipping provider search",
                    command.provider());
            return new Result(List.of(), false);
        }

        List<ProviderTripResult> trips = searchClient.search(
                command.provider(), originCityId.get(), destinationCityId.get(), command.travelDate());

        return new Result(trips, true);
    }

    /**
     * A mapping may exist while carrying only a station id — providers model geography
     * inconsistently. Search needs a city id specifically, so a station-only mapping counts as
     * unmapped for this purpose rather than being substituted with something that is not a city.
     */
    private Optional<String> cityIdFor(ProviderCode provider, LocationId locationId) {
        return mappingRepository.findByLocationAndProvider(locationId, provider)
                .map(ProviderLocationMapping::placeRef)
                .map(ref -> ref.cityId());
    }
}
