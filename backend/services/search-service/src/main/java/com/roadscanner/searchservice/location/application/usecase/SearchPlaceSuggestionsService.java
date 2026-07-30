package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteRateLimitedException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.in.SearchPlaceSuggestions;
import com.roadscanner.searchservice.location.domain.port.out.GooglePlacesClient;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;
import com.roadscanner.searchservice.location.domain.port.out.PlaceAutocompleteRateLimiter;
import com.roadscanner.searchservice.location.domain.port.out.PlaceSuggestionCache;

import java.util.List;
import java.util.Optional;

/**
 * Implements {@link SearchPlaceSuggestions}.
 *
 * <p>Owns three rules the adapters must not be trusted with:
 *
 * <ul>
 *   <li><strong>Clamp the limit.</strong> Every suggestion costs money at the provider, so a
 *       client cannot ask for an unbounded page on a keystroke.</li>
 *   <li><strong>Cache successes only.</strong> {@code put} is reached solely on the success path.
 *       A provider failure propagates as
 *       {@code PlaceAutocompleteUnavailableException} and writes nothing, so an outage is never
 *       frozen into the cache for its whole TTL.</li>
 *   <li><strong>Enrich, never write.</strong> Suggestions already present in the catalogue are
 *       annotated with their {@code LocationId}. Nothing is inserted: Google does not author the
 *       catalogue, and it does not create provider mappings.</li>
 * </ul>
 *
 * <p>Enrichment runs on the cached path too. Caching the provider's raw answer rather than the
 * enriched one is deliberate — the catalogue changes independently of Google, so a location
 * curated a minute ago must show up as curated immediately instead of waiting out the TTL.
 */
public class SearchPlaceSuggestionsService implements SearchPlaceSuggestions {

    /** Upper bound regardless of what the caller asks for. */
    static final int MAX_LIMIT = 10;

    private final GooglePlacesClient placesClient;
    private final PlaceSuggestionCache cache;
    private final PlaceAutocompleteRateLimiter rateLimiter;
    private final LocationRepository locationRepository;

    public SearchPlaceSuggestionsService(GooglePlacesClient placesClient, PlaceSuggestionCache cache,
                                         PlaceAutocompleteRateLimiter rateLimiter,
                                         LocationRepository locationRepository) {
        this.placesClient = placesClient;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
        this.locationRepository = locationRepository;
    }

    @Override
    public SearchPlaceSuggestionsResult search(SearchPlaceSuggestionsCommand command) {
        int effectiveLimit = Math.min(command.limit(), MAX_LIMIT);

        Optional<List<PlaceSuggestion>> cached = cache.get(command.query(), effectiveLimit);
        if (cached.isPresent()) {
            return new SearchPlaceSuggestionsResult(enrich(cached.get()), true);
        }

        // Checked after the cache, never before: a cache hit costs the provider nothing, so
        // spending a permit on it would throttle traffic that was never going to reach Google.
        if (!rateLimiter.tryAcquire()) {
            throw new PlaceAutocompleteRateLimitedException(
                    "Place autocomplete rate limit exceeded for this instance");
        }

        // A failure here propagates. Nothing below it runs, so nothing is cached.
        List<PlaceSuggestion> fromProvider = placesClient.autocomplete(command.query(), effectiveLimit);
        cache.put(command.query(), effectiveLimit, fromProvider);

        return new SearchPlaceSuggestionsResult(enrich(fromProvider), false);
    }

    /**
     * Annotates suggestions the catalogue already holds. A read-only join on {@code
     * google_place_id}, which V2 made unique precisely so this lookup is unambiguous.
     */
    private List<PlaceSuggestion> enrich(List<PlaceSuggestion> suggestions) {
        return suggestions.stream()
                .map(this::withCatalogueIdentity)
                .toList();
    }

    private PlaceSuggestion withCatalogueIdentity(PlaceSuggestion suggestion) {
        GooglePlaceId placeId = suggestion.googlePlaceId();
        return locationRepository.findByGooglePlaceId(placeId)
                .map(location -> suggestion.curatedAs(location.id()))
                .orElse(suggestion);
    }
}
