package com.roadscanner.searchservice.location.adapter.out.googleplaces;

import com.roadscanner.searchservice.config.GooglePlacesProperties;
import com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteUnavailableException;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.out.GooglePlacesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Translation of Google's wire format, and — the part that actually matters — which of Google's
 * answers count as failures.
 *
 * <p>Google signals application-level failure inside a 200 response, so a naive adapter treats
 * {@code REQUEST_DENIED} as "no results". That would be cached, turning a billing or key problem
 * into a silently empty dropdown for the whole TTL. These tests pin the distinction.
 */
class GooglePlacesAdapterTest {

    private static final String BASE_URL = "https://places.test/maps/api";
    private static final String AUTOCOMPLETE = BASE_URL + "/place/autocomplete/json";

    private MockRestServiceServer mockServer;
    private GooglePlacesClient adapter;

    private static GooglePlacesProperties properties(boolean enabled, String apiKey) {
        return new GooglePlacesProperties(enabled, apiKey, BASE_URL, Duration.ofSeconds(2),
                Duration.ofMinutes(10), 5_000, "en", "IN");
    }

    @BeforeEach
    void setUp() {
        setUpWith(properties(true, "test-key"));
    }

    private void setUpWith(GooglePlacesProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new GooglePlacesAdapter(builder.build(), properties);
    }

    private void respondWith(String body) {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(AUTOCOMPLETE)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void mapsPredictionsToSuggestions() {
        respondWith("""
                {"status":"OK","predictions":[
                  {"place_id":"place-hyd","description":"Hyderabad, Telangana, India",
                   "structured_formatting":{"main_text":"Hyderabad","secondary_text":"Telangana, India"}}
                ]}""");

        List<PlaceSuggestion> suggestions = adapter.autocomplete("hyd", 5);

        assertThat(suggestions).hasSize(1);
        PlaceSuggestion suggestion = suggestions.getFirst();
        assertThat(suggestion.googlePlaceId().value()).isEqualTo("place-hyd");
        assertThat(suggestion.description()).isEqualTo("Hyderabad, Telangana, India");
        assertThat(suggestion.primaryText()).isEqualTo("Hyderabad");
        assertThat(suggestion.secondaryText()).isEqualTo("Telangana, India");
        // The adapter suggests; it never claims catalogue identity.
        assertThat(suggestion.isCurated()).isFalse();
        mockServer.verify();
    }

    @Test
    void sendsTheQueryAndTheApiKey() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(AUTOCOMPLETE)))
                .andExpect(queryParam("input", "hyd"))
                .andExpect(queryParam("key", "test-key"))
                .andExpect(queryParam("language", "en"))
                .andExpect(queryParam("components", "country:IN"))
                .andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\"}", MediaType.APPLICATION_JSON));

        adapter.autocomplete("hyd", 5);

        mockServer.verify();
    }

    @Test
    void treatsZeroResultsAsAGenuineEmptyAnswer() {
        respondWith("{\"status\":\"ZERO_RESULTS\",\"predictions\":[]}");

        // A real, cacheable answer — Google knows of nothing matching. Not a failure.
        assertThat(adapter.autocomplete("zzz", 5)).isEmpty();
    }

    @Test
    void treatsRequestDeniedAsAFailureDespiteTheHttp200() {
        respondWith("{\"status\":\"REQUEST_DENIED\",\"error_message\":\"The provided API key is invalid.\"}");

        assertThatThrownBy(() -> adapter.autocomplete("hyd", 5))
                .isInstanceOf(PlaceAutocompleteUnavailableException.class)
                .hasMessageContaining("REQUEST_DENIED");
    }

    @Test
    void treatsOverQueryLimitAsAFailure() {
        respondWith("{\"status\":\"OVER_QUERY_LIMIT\"}");

        assertThatThrownBy(() -> adapter.autocomplete("hyd", 5))
                .isInstanceOf(PlaceAutocompleteUnavailableException.class);
    }

    @Test
    void treatsATransportFailureAsAFailure() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(AUTOCOMPLETE)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.autocomplete("hyd", 5))
                .isInstanceOf(PlaceAutocompleteUnavailableException.class);
    }

    @Test
    void treatsAnUnparseableBodyAsAFailureRatherThanAnEmptyResult() {
        respondWith("{}");

        assertThatThrownBy(() -> adapter.autocomplete("hyd", 5))
                .isInstanceOf(PlaceAutocompleteUnavailableException.class);
    }

    @Test
    void appliesTheLimit() {
        respondWith("""
                {"status":"OK","predictions":[
                  {"place_id":"p1","description":"One"},
                  {"place_id":"p2","description":"Two"},
                  {"place_id":"p3","description":"Three"}
                ]}""");

        assertThat(adapter.autocomplete("hyd", 2)).hasSize(2);
    }

    @Test
    void dropsPredictionsThatCarryNoPlaceId() {
        respondWith("""
                {"status":"OK","predictions":[
                  {"description":"A query prediction with no place id"},
                  {"place_id":"place-hyd","description":"Hyderabad, Telangana, India"}
                ]}""");

        // Without a place id the candidate can never be promoted into the catalogue, so showing
        // it would offer the user something that cannot be acted on.
        assertThat(adapter.autocomplete("hyd", 5)).extracting(s -> s.googlePlaceId().value())
                .containsExactly("place-hyd");
    }

    @Test
    void fallsBackToTheDescriptionWhenGoogleOmitsStructuredFormatting() {
        respondWith("{\"status\":\"OK\",\"predictions\":[{\"place_id\":\"p1\",\"description\":\"Hyderabad\"}]}");

        PlaceSuggestion suggestion = adapter.autocomplete("hyd", 5).getFirst();

        assertThat(suggestion.primaryText()).isEqualTo("Hyderabad");
        assertThat(suggestion.secondaryText()).isNull();
    }

    @Test
    void ignoresUnknownFieldsGoogleMayAddLater() {
        respondWith("""
                {"status":"OK","some_new_field":{"nested":true},"predictions":[
                  {"place_id":"p1","description":"Hyderabad","types":["locality"],"future_field":1}
                ]}""");

        // A field Google adds next quarter must not break autocomplete for every user.
        assertThat(adapter.autocomplete("hyd", 5)).hasSize(1);
    }

    @Test
    void failsFastWhenNoApiKeyIsConfigured() {
        setUpWith(properties(true, "  "));

        assertThatThrownBy(() -> adapter.autocomplete("hyd", 5))
                .isInstanceOf(PlaceAutocompleteUnavailableException.class)
                .hasMessageContaining("not configured");

        // Never even attempted — a missing key must not become a 401 storm against Google.
        mockServer.verify();
    }

    @Test
    void failsFastWhenTheIntegrationIsDisabled() {
        setUpWith(properties(false, "test-key"));

        assertThatThrownBy(() -> adapter.autocomplete("hyd", 5))
                .isInstanceOf(PlaceAutocompleteUnavailableException.class);

        mockServer.verify();
    }
}
