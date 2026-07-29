package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.searchservice.config.SecurityConfig;
import com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteUnavailableException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.in.SearchPlaceSuggestions;
import com.roadscanner.searchservice.testsupport.security.NoOpJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public place-autocomplete contract: anonymous access, status mapping, and the guarantee
 * that the Google API key never appears in a response.
 */
@WebMvcTest(GooglePlacesController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, SecurityConfig.class,
        NoOpJwtDecoderConfig.class})
class GooglePlacesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchPlaceSuggestions searchPlaceSuggestions;

    private static PlaceSuggestion hyderabad() {
        return PlaceSuggestion.uncurated(new GooglePlaceId("place-hyd"), "Hyderabad, Telangana, India",
                "Hyderabad", "Telangana, India");
    }

    private void stub(List<PlaceSuggestion> suggestions, boolean cached) {
        when(searchPlaceSuggestions.search(any()))
                .thenReturn(new SearchPlaceSuggestions.SearchPlaceSuggestionsResult(suggestions, cached));
    }

    @Test
    void returnsSuggestionsAnonymously() {
        stub(List.of(hyderabad()), false);

        // No token anywhere: a traveller typing a destination is not logged in.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                mockMvc.perform(get("/api/v1/google/places").param("q", "hyd"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.suggestions[0].googlePlaceId").value("place-hyd"))
                        .andExpect(jsonPath("$.suggestions[0].description").value("Hyderabad, Telangana, India"))
                        .andExpect(jsonPath("$.suggestions[0].primaryText").value("Hyderabad"))
                        .andExpect(jsonPath("$.suggestions[0].secondaryText").value("Telangana, India"))
                        .andExpect(jsonPath("$.suggestions[0].curated").value(false))
                        .andExpect(jsonPath("$.suggestions[0].locationId").doesNotExist())
                        .andExpect(jsonPath("$.cached").value(false)));
    }

    @Test
    void surfacesCatalogueIdentityForACuratedPlace() throws Exception {
        LocationId locationId = LocationId.generate();
        stub(List.of(hyderabad().curatedAs(locationId)), false);

        mockMvc.perform(get("/api/v1/google/places").param("q", "hyd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].curated").value(true))
                .andExpect(jsonPath("$.suggestions[0].locationId").value(locationId.toString()));
    }

    @Test
    void neverLeaksTheApiKeyInAResponse() throws Exception {
        stub(List.of(hyderabad()), false);

        String body = mockMvc.perform(get("/api/v1/google/places").param("q", "hyd"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The whole reason this proxy endpoint exists — the browser must never see the key.
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("apiKey", "api_key", "key=", "GOOGLE_PLACES_API_KEY");
    }

    @Test
    void reportsWhenAnAnswerCameFromCache() throws Exception {
        stub(List.of(hyderabad()), true);

        mockMvc.perform(get("/api/v1/google/places").param("q", "hyd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true));
    }

    @Test
    void returnsAnEmptyEnvelopeRatherThanNotFound() throws Exception {
        stub(List.of(), false);

        mockMvc.perform(get("/api/v1/google/places").param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isEmpty());
    }

    @Test
    void defaultsTheLimitWhenNotSupplied() throws Exception {
        stub(List.of(), false);

        mockMvc.perform(get("/api/v1/google/places").param("q", "hyd")).andExpect(status().isOk());

        verify(searchPlaceSuggestions).search(argThat(command -> command.limit() == 5));
    }

    @Test
    void passesAnExplicitLimitThrough() throws Exception {
        stub(List.of(), false);

        mockMvc.perform(get("/api/v1/google/places").param("q", "hyd").param("limit", "3"))
                .andExpect(status().isOk());

        verify(searchPlaceSuggestions).search(argThat(command -> command.limit() == 3));
    }

    @Test
    void rejectsABlankQueryWithoutCallingTheUseCase() throws Exception {
        mockMvc.perform(get("/api/v1/google/places").param("q", "   "))
                .andExpect(status().isBadRequest());

        verify(searchPlaceSuggestions, never()).search(any());
    }

    @Test
    void rejectsAMissingQuery() throws Exception {
        mockMvc.perform(get("/api/v1/google/places")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOutOfRangeLimit() throws Exception {
        mockMvc.perform(get("/api/v1/google/places").param("q", "hyd").param("limit", "50"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/google/places").param("q", "hyd").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAProviderOutageAsServiceUnavailable() throws Exception {
        when(searchPlaceSuggestions.search(any()))
                .thenThrow(new PlaceAutocompleteUnavailableException("provider down"));

        // 503, not an empty 200 — a client must be able to tell an outage from "no matches".
        mockMvc.perform(get("/api/v1/google/places").param("q", "hyd"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Place suggestions are temporarily unavailable"));
    }

    @Test
    void doesNotLeakProviderDetailInTheErrorBody() throws Exception {
        when(searchPlaceSuggestions.search(any()))
                .thenThrow(new PlaceAutocompleteUnavailableException("REQUEST_DENIED: key AIzaSyExampleKey invalid"));

        String body = mockMvc.perform(get("/api/v1/google/places").param("q", "hyd"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        // The raw provider message can carry the key or billing detail; it belongs in the log.
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("AIzaSyExampleKey", "REQUEST_DENIED");
    }
}
