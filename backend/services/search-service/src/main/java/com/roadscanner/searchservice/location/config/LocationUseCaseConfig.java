package com.roadscanner.searchservice.location.config;

import com.roadscanner.searchservice.location.application.usecase.CreateLocationService;
import com.roadscanner.searchservice.location.application.usecase.DisableLocationService;
import com.roadscanner.searchservice.location.application.usecase.GetLocationService;
import com.roadscanner.searchservice.location.application.usecase.GetProviderMappingService;
import com.roadscanner.searchservice.location.application.usecase.ManageProviderMappingsService;
import com.roadscanner.searchservice.location.application.usecase.SearchProviderMappingsService;
import com.roadscanner.searchservice.location.domain.port.in.ManageProviderMappings;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderMappings;
import com.roadscanner.searchservice.location.application.usecase.SearchLocationsService;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.application.usecase.SearchPlaceSuggestionsService;
import com.roadscanner.searchservice.location.application.usecase.SearchProviderTripsService;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips;
import com.roadscanner.searchservice.location.application.usecase.UpdateLocationService;
import com.roadscanner.searchservice.location.domain.port.in.CreateLocation;
import com.roadscanner.searchservice.location.domain.port.in.DisableLocation;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.in.GetProviderMapping;
import com.roadscanner.searchservice.location.domain.port.in.SearchLocations;
import com.roadscanner.searchservice.location.domain.port.in.SearchPlaceSuggestions;
import com.roadscanner.searchservice.location.domain.port.in.UpdateLocation;
import com.roadscanner.searchservice.location.domain.port.out.GooglePlacesClient;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;
import com.roadscanner.searchservice.location.domain.port.out.PlaceAutocompleteRateLimiter;
import com.roadscanner.searchservice.location.domain.port.out.PlaceSuggestionCache;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Explicit bean wiring for the location module's use cases, mirroring the service's existing
 * {@code UseCaseConfig}: the application classes carry no Spring stereotype annotations and are
 * plain constructors wired here, so every dependency of every use case is visible in one place
 * and the application layer stays framework-light.
 *
 * <p>Kept separate from the root {@code UseCaseConfig} rather than merged into it, so the module
 * stays self-contained — a later decision to extract it into its own service is a package move,
 * not an untangling exercise.
 */
@Configuration
public class LocationUseCaseConfig {

    /**
     * A single injectable clock for the module. Declared {@code @ConditionalOnMissingBean}-free
     * on purpose: if the wider service later defines its own {@code Clock} bean this will clash
     * loudly rather than silently binding two different notions of "now".
     */
    @Bean
    public Clock locationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SearchLocations searchLocations(LocationRepository repository) {
        return new SearchLocationsService(repository);
    }

    @Bean
    public GetLocation getLocation(LocationRepository repository) {
        return new GetLocationService(repository);
    }

    @Bean
    public CreateLocation createLocation(LocationRepository repository, Clock locationClock) {
        return new CreateLocationService(repository, locationClock);
    }

    @Bean
    public UpdateLocation updateLocation(LocationRepository repository, Clock locationClock) {
        return new UpdateLocationService(repository, locationClock);
    }

    @Bean
    public DisableLocation disableLocation(LocationRepository repository, Clock locationClock) {
        return new DisableLocationService(repository, locationClock);
    }

    @Bean
    public GetProviderMapping getProviderMapping(LocationRepository locationRepository,
                                                 ProviderLocationMappingRepository mappingRepository) {
        return new GetProviderMappingService(locationRepository, mappingRepository);
    }

    /**
     * Authoring the translation layer. Takes {@link LocationRepository} so it can refuse a mapping
     * for a location that does not exist — a mapping translates a place the catalogue already
     * holds, and never brings one into being.
     */
    @Bean
    public ManageProviderMappings manageProviderMappings(LocationRepository locationRepository,
                                                          ProviderLocationMappingRepository mappingRepository,
                                                          Clock locationClock) {
        return new ManageProviderMappingsService(locationRepository, mappingRepository, locationClock);
    }

    /** The administrative listing, which resolves each mapping against the location it translates. */
    @Bean
    public SearchProviderMappings searchProviderMappings(ProviderLocationMappingRepository mappingRepository,
                                                          LocationRepository locationRepository) {
        return new SearchProviderMappingsService(mappingRepository, locationRepository);
    }

    /**
     * Place autocomplete. Takes {@link LocationRepository} only to resolve which suggestions are
     * already curated — it reads, and cannot write to the catalogue.
     */
    @Bean
    public SearchPlaceSuggestions searchPlaceSuggestions(GooglePlacesClient placesClient,
                                                         PlaceSuggestionCache cache,
                                                         PlaceAutocompleteRateLimiter rateLimiter,
                                                         LocationRepository locationRepository) {
        return new SearchPlaceSuggestionsService(placesClient, cache, rateLimiter, locationRepository);
    }

    /**
     * Provider trip search. Takes the mapping repository so translation happens against the
     * platform's single mapping table, and the client port so the provider call itself stays on
     * the far side of the service boundary.
     */
    @Bean
    public SearchProviderTrips searchProviderTrips(ProviderLocationMappingRepository mappingRepository,
                                                   ProviderTripSearchClient providerTripSearchClient) {
        return new SearchProviderTripsService(mappingRepository, providerTripSearchClient);
    }
}
