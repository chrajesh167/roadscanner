package com.roadscanner.searchservice.location.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.searchservice.config.SecurityConfig;
import com.roadscanner.searchservice.testsupport.security.NoOpJwtDecoderConfig;
import com.roadscanner.searchservice.location.domain.exception.DuplicateGooglePlaceIdException;
import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.in.CreateLocation;
import com.roadscanner.searchservice.location.domain.port.in.DisableLocation;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.in.SearchLocations;
import com.roadscanner.searchservice.location.domain.port.in.UpdateLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of the location catalogue: request binding, DTO validation, status-code
 * mapping, and — most importantly — that no response body ever carries a provider identifier.
 *
 * <p>Use cases are mocked; their behaviour is covered by {@code LocationUseCasesTest}. What is
 * under test here is only the adapter: what a client sends and what a client gets back.
 */
@WebMvcTest(LocationController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, SecurityConfig.class,
        NoOpJwtDecoderConfig.class})
class LocationControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final LocationAddress HYDERABAD = new LocationAddress("Hyderabad", "Telangana", "India");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchLocations searchLocations;

    @MockBean
    private GetLocation getLocation;

    @MockBean
    private CreateLocation createLocation;

    @MockBean
    private UpdateLocation updateLocation;

    @MockBean
    private DisableLocation disableLocation;

    private static Location hyderabad() {
        return Location.reconstitute(LocationId.generate(), "Hyderabad", HYDERABAD,
                new GeoCoordinates(new BigDecimal("17.3850000"), new BigDecimal("78.4867000")),
                new GooglePlaceId("place-hyd"), "Asia/Kolkata", true, NOW, NOW);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> validRequest() {
        return Map.of(
                "displayName", "Hyderabad",
                "city", "Hyderabad",
                "state", "Telangana",
                "country", "India",
                "timezone", "Asia/Kolkata");
    }

    /**
     * An authenticated ADMIN. The authority is set directly rather than left to the {@code role}
     * claim, because {@code jwt()} injects a ready-made {@code Authentication} and never runs
     * {@code SecurityConfig}'s claim→authority converter. That converter is covered end to end,
     * against a genuinely signed token, by {@code LocationCatalogueEndToEndTest}.
     */
    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.ADMIN_ROLE));
    }

    private static RequestPostProcessor traveler() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_TRAVELER"));
    }

    // ---------- GET /api/v1/locations ----------

    @Test
    void autocompleteReturnsSummariesForAQuery() throws Exception {
        Location location = hyderabad();
        when(searchLocations.search(any())).thenReturn(new SearchLocations.SearchLocationsResult(List.of(location)));

        mockMvc.perform(get("/api/v1/locations").param("q", "hyd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].id").value(location.id().toString()))
                .andExpect(jsonPath("$.suggestions[0].displayName").value("Hyderabad"))
                .andExpect(jsonPath("$.suggestions[0].city").value("Hyderabad"))
                .andExpect(jsonPath("$.suggestions[0].state").value("Telangana"))
                .andExpect(jsonPath("$.suggestions[0].country").value("India"));
    }

    @Test
    void autocompleteKeepsItsPayloadNarrow() throws Exception {
        when(searchLocations.search(any())).thenReturn(new SearchLocations.SearchLocationsResult(List.of(hyderabad())));

        // A suggestion fires on every keystroke — it must not drag coordinates, audit fields,
        // or anything provider-shaped along with it.
        mockMvc.perform(get("/api/v1/locations").param("q", "hyd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.suggestions[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.suggestions[0].googlePlaceId").doesNotExist())
                .andExpect(jsonPath("$.suggestions[0].provider").doesNotExist());
    }

    @Test
    void autocompleteReturnsAnEmptyEnvelopeRatherThanNotFound() throws Exception {
        when(searchLocations.search(any())).thenReturn(new SearchLocations.SearchLocationsResult(List.of()));

        mockMvc.perform(get("/api/v1/locations").param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isEmpty());
    }

    @Test
    void autocompleteDefaultsTheLimitWhenNotSupplied() throws Exception {
        when(searchLocations.search(any())).thenReturn(new SearchLocations.SearchLocationsResult(List.of()));

        mockMvc.perform(get("/api/v1/locations").param("q", "hyd")).andExpect(status().isOk());

        verify(searchLocations).search(argThat(command -> command.limit() == 10));
    }

    @Test
    void autocompletePassesAnExplicitLimitThrough() throws Exception {
        when(searchLocations.search(any())).thenReturn(new SearchLocations.SearchLocationsResult(List.of()));

        mockMvc.perform(get("/api/v1/locations").param("q", "hyd").param("limit", "5"))
                .andExpect(status().isOk());

        verify(searchLocations).search(argThat(command -> command.limit() == 5));
    }

    @Test
    void autocompleteRejectsABlankQuery() throws Exception {
        mockMvc.perform(get("/api/v1/locations").param("q", "   "))
                .andExpect(status().isBadRequest());

        verify(searchLocations, never()).search(any());
    }

    @Test
    void autocompleteRejectsAMissingQuery() throws Exception {
        mockMvc.perform(get("/api/v1/locations")).andExpect(status().isBadRequest());
    }

    @Test
    void autocompleteRejectsAnOutOfRangeLimit() throws Exception {
        mockMvc.perform(get("/api/v1/locations").param("q", "hyd").param("limit", "500"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/locations").param("q", "hyd").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/v1/locations/{id} ----------

    @Test
    void getReturnsTheFullLocation() throws Exception {
        Location location = hyderabad();
        when(getLocation.get(any())).thenReturn(new GetLocation.GetLocationResult(location));

        mockMvc.perform(get("/api/v1/locations/{id}", location.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(location.id().toString()))
                .andExpect(jsonPath("$.displayName").value("Hyderabad"))
                .andExpect(jsonPath("$.country").value("India"))
                .andExpect(jsonPath("$.latitude").value(17.3850000))
                .andExpect(jsonPath("$.longitude").value(78.4867000))
                .andExpect(jsonPath("$.googlePlaceId").value("place-hyd"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getNeverExposesProviderIdentifiers() throws Exception {
        when(getLocation.get(any())).thenReturn(new GetLocation.GetLocationResult(hyderabad()));

        // The contract this whole module exists to enforce: the platform speaks RoadScanner ids
        // only, so no provider vocabulary may appear in a public response.
        mockMvc.perform(get("/api/v1/locations/{id}", LocationId.generate().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.providerCityId").doesNotExist())
                .andExpect(jsonPath("$.providerStationId").doesNotExist())
                .andExpect(jsonPath("$.providerMappings").doesNotExist());
    }

    @Test
    void getStillReturnsASoftDeletedLocation() throws Exception {
        Location withdrawn = Location.reconstitute(LocationId.generate(), "Hyderguda", HYDERABAD, null, null, null,
                false, NOW, NOW);
        when(getLocation.get(any())).thenReturn(new GetLocation.GetLocationResult(withdrawn));

        mockMvc.perform(get("/api/v1/locations/{id}", withdrawn.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getReturnsNotFoundForAnUnknownId() throws Exception {
        LocationId unknown = LocationId.generate();
        when(getLocation.get(any())).thenThrow(new LocationNotFoundException(unknown));

        mockMvc.perform(get("/api/v1/locations/{id}", unknown.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Location not found"));
    }

    @Test
    void getReturnsBadRequestForAMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/locations/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /api/v1/locations ----------

    @Test
    void createReturnsCreatedWithALocationHeader() throws Exception {
        Location created = hyderabad();
        when(createLocation.create(any())).thenReturn(new CreateLocation.CreateLocationResult(created));

        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest()))
                        .with(admin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/locations/" + created.id()))
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createMapsTheRequestOntoDomainValueObjects() throws Exception {
        when(createLocation.create(any())).thenReturn(new CreateLocation.CreateLocationResult(hyderabad()));
        Map<String, Object> request = Map.of(
                "displayName", "Hyderabad",
                "city", "Hyderabad",
                "state", "Telangana",
                "country", "India",
                "latitude", "17.3850000",
                "longitude", "78.4867000",
                "googlePlaceId", "place-hyd",
                "timezone", "Asia/Kolkata");

        mockMvc.perform(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content(json(request))
                        .with(admin()))
                .andExpect(status().isCreated());

        verify(createLocation).create(argThat(command ->
                command.address().equals(HYDERABAD)
                        && command.googlePlaceId().equals(new GooglePlaceId("place-hyd"))
                        && command.coordinates().latitude().compareTo(new BigDecimal("17.3850000")) == 0));
    }

    @Test
    void createAcceptsARequestWithOnlyTheRequiredFields() throws Exception {
        when(createLocation.create(any())).thenReturn(new CreateLocation.CreateLocationResult(hyderabad()));

        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "Hyderabad", "city", "Hyderabad", "country", "India")))
                        .with(admin()))
                .andExpect(status().isCreated());

        verify(createLocation).create(argThat(command ->
                command.coordinates() == null && command.googlePlaceId() == null && command.timezone() == null));
    }

    @Test
    void createRejectsAMissingDisplayName() throws Exception {
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("city", "Hyderabad", "country", "India")))
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));

        verify(createLocation, never()).create(any());
    }

    @Test
    void createRejectsABlankCity() throws Exception {
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "Hyderabad", "city", "  ", "country", "India")))
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("city"));
    }

    @Test
    void createRejectsAMissingCountry() throws Exception {
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "Hyderabad", "city", "Hyderabad")))
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("country"));
    }

    @Test
    void createRejectsAnOverlongDisplayName() throws Exception {
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "x".repeat(256), "city", "Hyderabad", "country", "India")))
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
    }

    @Test
    void createRejectsAnOutOfRangeLatitude() throws Exception {
        Map<String, Object> request = Map.of("displayName", "Hyderabad", "city", "Hyderabad", "country", "India",
                "latitude", "91", "longitude", "78.4867000");

        mockMvc.perform(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content(json(request))
                        .with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsAHalfSuppliedCoordinate() throws Exception {
        Map<String, Object> request = Map.of("displayName", "Hyderabad", "city", "Hyderabad", "country", "India",
                "latitude", "17.3850000");

        // A lone latitude is not a point — the value object rejects it and the handler turns
        // that into a 400 rather than a 500.
        mockMvc.perform(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content(json(request))
                        .with(admin()))
                .andExpect(status().isBadRequest());

        verify(createLocation, never()).create(any());
    }

    @Test
    void createReturnsConflictWhenTheGooglePlaceIdIsAlreadyTaken() throws Exception {
        when(createLocation.create(any())).thenThrow(new DuplicateGooglePlaceIdException(new GooglePlaceId("place-hyd")));

        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest()))
                        .with(admin()))
                .andExpect(status().isConflict());
    }

    // ---------- PUT /api/v1/locations/{id} ----------

    @Test
    void updateReturnsTheReplacedLocation() throws Exception {
        Location updated = hyderabad();
        when(updateLocation.update(any())).thenReturn(new UpdateLocation.UpdateLocationResult(updated));

        mockMvc.perform(put("/api/v1/locations/{id}", updated.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest()))
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(updated.id().toString()))
                .andExpect(jsonPath("$.displayName").value("Hyderabad"));
    }

    @Test
    void updateTargetsThePathIdRatherThanAnythingInTheBody() throws Exception {
        Location updated = hyderabad();
        LocationId pathId = LocationId.generate();
        when(updateLocation.update(any())).thenReturn(new UpdateLocation.UpdateLocationResult(updated));

        mockMvc.perform(put("/api/v1/locations/{id}", pathId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest()))
                        .with(admin()))
                .andExpect(status().isOk());

        verify(updateLocation).update(argThat(command -> command.locationId().equals(pathId)));
    }

    @Test
    void updateClearsOmittedOptionalFields() throws Exception {
        when(updateLocation.update(any())).thenReturn(new UpdateLocation.UpdateLocationResult(hyderabad()));

        mockMvc.perform(put("/api/v1/locations/{id}", LocationId.generate().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "Hyderabad", "city", "Hyderabad", "country", "India")))
                        .with(admin()))
                .andExpect(status().isOk());

        // PUT is a full replace: an omitted field means "clear it", not "leave it alone".
        verify(updateLocation).update(argThat(command ->
                command.coordinates() == null && command.googlePlaceId() == null && command.timezone() == null));
    }

    @Test
    void updateReturnsNotFoundForAnUnknownId() throws Exception {
        LocationId unknown = LocationId.generate();
        when(updateLocation.update(any())).thenThrow(new LocationNotFoundException(unknown));

        mockMvc.perform(put("/api/v1/locations/{id}", unknown.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest()))
                        .with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReturnsConflictWhenTheGooglePlaceIdBelongsToAnotherLocation() throws Exception {
        when(updateLocation.update(any()))
                .thenThrow(new DuplicateGooglePlaceIdException(new GooglePlaceId("place-hyd")));

        mockMvc.perform(put("/api/v1/locations/{id}", LocationId.generate().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest()))
                        .with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void updateAppliesTheSameValidationAsCreate() throws Exception {
        mockMvc.perform(put("/api/v1/locations/{id}", LocationId.generate().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("city", "Hyderabad", "country", "India")))
                        .with(admin()))
                .andExpect(status().isBadRequest());

        verify(updateLocation, never()).update(any());
    }

    // ---------- DELETE /api/v1/locations/{id} ----------

    @Test
    void deleteReturnsNoContent() throws Exception {
        LocationId id = LocationId.generate();
        when(disableLocation.disable(any())).thenReturn(new DisableLocation.DisableLocationResult(id, false));

        mockMvc.perform(delete("/api/v1/locations/{id}", id.value())
                        .with(admin()))
                .andExpect(status().isNoContent());

        verify(disableLocation).disable(argThat(command -> command.locationId().equals(id)));
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        LocationId id = LocationId.generate();
        when(disableLocation.disable(any())).thenReturn(new DisableLocation.DisableLocationResult(id, true));

        // A repeated withdrawal is still a success — a retried DELETE must not surface as an error.
        mockMvc.perform(delete("/api/v1/locations/{id}", id.value())
                        .with(admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturnsNotFoundForAnUnknownId() throws Exception {
        LocationId unknown = LocationId.generate();
        when(disableLocation.disable(any())).thenThrow(new LocationNotFoundException(unknown));

        mockMvc.perform(delete("/api/v1/locations/{id}", unknown.value())
                        .with(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- Authorization ----------
    //
    // The catalogue's write side authors the canonical location data the whole platform resolves
    // against, so it is gated at this service, not only at api-gateway. The reads stay anonymous.

    @Test
    void writesAreRejectedWithoutTheAdminRole() throws Exception {
        LocationId id = LocationId.generate();

        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest()))
                        .with(traveler()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/locations/{id}", id.value())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest()))
                        .with(traveler()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/locations/{id}", id.value())
                        .with(traveler()))
                .andExpect(status().isForbidden());

        // Authorization must be decided before the request ever reaches a use case — a rejected
        // caller must not be able to cause a write, or even a lookup.
        verify(createLocation, never()).create(any());
        verify(updateLocation, never()).update(any());
        verify(disableLocation, never()).disable(any());
    }

    @Test
    void writesAreRejectedWithNoTokenAtAll() throws Exception {
        LocationId id = LocationId.generate();

        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/locations/{id}", id.value())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/locations/{id}", id.value()))
                .andExpect(status().isUnauthorized());

        verify(createLocation, never()).create(any());
        verify(updateLocation, never()).update(any());
        verify(disableLocation, never()).disable(any());
    }

    @Test
    void readsStayAnonymousExactlyAsBefore() throws Exception {
        when(searchLocations.search(any())).thenReturn(new SearchLocations.SearchLocationsResult(List.of()));
        when(getLocation.get(any())).thenReturn(new GetLocation.GetLocationResult(hyderabad()));

        // No .with(...) anywhere here: adding security must not have cost the public read surface
        // its anonymous access.
        mockMvc.perform(get("/api/v1/locations").param("q", "hyd"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/locations/{id}", LocationId.generate().value()))
                .andExpect(status().isOk());
    }
}
