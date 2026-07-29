package com.roadscanner.searchservice.location.adapter.out.googleplaces;

import com.roadscanner.searchservice.config.GooglePlacesProperties;
import com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteUnavailableException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.out.GooglePlacesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Calls Google Places Autocomplete and translates its answer into {@link PlaceSuggestion}s.
 *
 * <p>The only class in the service that knows Google's wire format exists here; the domain sees
 * suggestions, never a {@code predictions} array. Swapping providers is a sibling adapter.
 *
 * <p>Failure handling is the important part. Google distinguishes transport failure from
 * application-level failure — a 200 response carrying {@code "status": "REQUEST_DENIED"} is still
 * a failure — so both are translated to
 * {@link PlaceAutocompleteUnavailableException}. {@code ZERO_RESULTS} is the one status that is
 * genuinely a success with nothing in it, and is returned as an empty list so it can be cached.
 * Getting this split wrong is what would let an outage be cached as "no matches".
 */
@Component
class GooglePlacesAdapter implements GooglePlacesClient {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesAdapter.class);
    private static final String AUTOCOMPLETE_PATH = "/place/autocomplete/json";

    private static final String STATUS_OK = "OK";
    private static final String STATUS_ZERO_RESULTS = "ZERO_RESULTS";

    private final RestClient restClient;
    private final GooglePlacesProperties properties;

    GooglePlacesAdapter(RestClient googlePlacesRestClient, GooglePlacesProperties properties) {
        this.restClient = googlePlacesRestClient;
        this.properties = properties;
    }

    @Override
    public List<PlaceSuggestion> autocomplete(String query, int limit) {
        if (!properties.isUsable()) {
            // Configuration problem, not a transient one — but the caller's handling is the same,
            // and this keeps "no key" from turning into a 401 storm against Google.
            throw new PlaceAutocompleteUnavailableException(
                    "Google Places is not configured (roadscanner.google-places.enabled / GOOGLE_PLACES_API_KEY)");
        }

        GoogleAutocompleteResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(AUTOCOMPLETE_PATH)
                                .queryParam("input", query)
                                .queryParam("key", properties.apiKey());
                        if (properties.language() != null && !properties.language().isBlank()) {
                            uriBuilder.queryParam("language", properties.language());
                        }
                        if (properties.region() != null && !properties.region().isBlank()) {
                            uriBuilder.queryParam("components", "country:" + properties.region());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(GoogleAutocompleteResponse.class);
        } catch (RestClientException e) {
            // Never log the key, and never log the raw URI — it carries the key as a query param.
            log.warn("Google Places autocomplete call failed", e);
            throw new PlaceAutocompleteUnavailableException("Google Places autocomplete call failed", e);
        }

        return toSuggestions(response, limit);
    }

    private List<PlaceSuggestion> toSuggestions(GoogleAutocompleteResponse response, int limit) {
        if (response == null || response.status() == null) {
            throw new PlaceAutocompleteUnavailableException("Google Places returned no parseable body");
        }

        if (STATUS_ZERO_RESULTS.equals(response.status())) {
            // A real, cacheable answer: Google knows of nothing matching this fragment.
            return List.of();
        }

        if (!STATUS_OK.equals(response.status())) {
            // REQUEST_DENIED, OVER_QUERY_LIMIT, INVALID_REQUEST, UNKNOWN_ERROR — all failures
            // wearing a 200. errorMessage is Google's own text and is safe to log; it never
            // contains the key.
            log.warn("Google Places returned status={} message={}", response.status(), response.errorMessage());
            throw new PlaceAutocompleteUnavailableException(
                    "Google Places returned status " + response.status());
        }

        if (response.predictions() == null) {
            return List.of();
        }

        return response.predictions().stream()
                .filter(GooglePlacesAdapter::isUsable)
                .limit(limit)
                .map(GooglePlacesAdapter::toSuggestion)
                .toList();
    }

    /** Google occasionally returns a prediction with no place_id (query predictions); it cannot
     * be curated into the catalogue later, so it is dropped rather than shown. */
    private static boolean isUsable(GoogleAutocompleteResponse.Prediction prediction) {
        return prediction != null
                && prediction.placeId() != null && !prediction.placeId().isBlank()
                && prediction.description() != null && !prediction.description().isBlank();
    }

    private static PlaceSuggestion toSuggestion(GoogleAutocompleteResponse.Prediction prediction) {
        GoogleAutocompleteResponse.StructuredFormatting formatting = prediction.structuredFormatting();
        String primary = formatting != null && formatting.mainText() != null && !formatting.mainText().isBlank()
                ? formatting.mainText()
                : prediction.description();
        String secondary = formatting == null ? null : formatting.secondaryText();

        return PlaceSuggestion.uncurated(
                new GooglePlaceId(prediction.placeId()),
                prediction.description(),
                primary,
                secondary);
    }
}
