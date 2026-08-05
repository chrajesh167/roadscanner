package com.roadscanner.searchservice.location.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.searchservice.adapter.in.rest.exception.GlobalExceptionHandler;
import com.roadscanner.searchservice.adapter.in.rest.filter.CorrelationIdFilter;
import com.roadscanner.searchservice.config.SecurityConfig;
import com.roadscanner.searchservice.location.domain.exception.DuplicateProviderMappingException;
import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.in.ManageProviderMappings;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderMappings;
import com.roadscanner.searchservice.testsupport.security.NoOpJwtDecoderConfig;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
 * The HTTP contract of the administrative translation layer: request binding, validation, status
 * codes, and the authorization posture that makes this surface different from every other one in
 * the service.
 *
 * <p>Use cases are mocked — their behaviour belongs to {@code ManageProviderMappingsServiceTest}.
 * What is under test here is only the adapter.
 *
 * <p>The authorization tests carry the most weight. This is the one place in the service that
 * returns a provider's own identifiers, and unlike the location catalogue its <em>reads</em> are
 * gated too. A regression that opened a GET here would leak provider vocabulary onto an anonymous
 * surface while every functional test stayed green.
 */
@WebMvcTest(ProviderMappingController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, SecurityConfig.class,
        NoOpJwtDecoderConfig.class})
class ProviderMappingControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final LocationAddress HYDERABAD = new LocationAddress("Hyderabad", "Telangana", "India");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchProviderMappings searchProviderMappings;

    @MockBean
    private ManageProviderMappings manageProviderMappings;

    @MockBean
    private GetLocation getLocation;

    private static Location hyderabad() {
        return Location.reconstitute(LocationId.generate(), "Hyderabad", HYDERABAD,
                new GeoCoordinates(new BigDecimal("17.3850000"), new BigDecimal("78.4867000")),
                new GooglePlaceId("place-hyd"), "Asia/Kolkata", true, NOW, NOW);
    }

    private static ProviderLocationMapping mappingFor(Location location) {
        return ProviderLocationMapping.reconstitute(ProviderLocationMappingId.generate(), FLIXBUS,
                location.id(), new ProviderPlaceRef("58291", "station-1", "MGBS"),
                "{\"platform\":\"7\"}", true, NOW, NOW, NOW);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** A create body: a location, a provider, and at least one provider-side identifier. */
    private Map<String, Object> validRequest(LocationId locationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("locationId", locationId.value().toString());
        body.put("provider", "FLIXBUS");
        body.put("providerCityId", "58291");
        body.put("providerStationName", "MGBS");
        body.put("verified", false);
        return body;
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.ADMIN_ROLE));
    }

    private static RequestPostProcessor traveler() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_TRAVELER"));
    }

    private void stubGetLocation(Location location) {
        when(getLocation.get(any())).thenReturn(new GetLocation.GetLocationResult(location));
    }

    // ---------- GET /api/v1/provider-mappings ----------

    @Test
    void listReturnsAPageWithBothHalvesOfEachTranslation() throws Exception {
        Location location = hyderabad();
        ProviderLocationMapping mapping = mappingFor(location);
        when(searchProviderMappings.search(any())).thenReturn(new SearchProviderMappings.Result(
                List.of(new SearchProviderMappings.MappedLocation(mapping, location)), 1, 0, 20, 1));

        mockMvc.perform(get("/api/v1/provider-mappings").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappings[0].id").value(mapping.id().toString()))
                .andExpect(jsonPath("$.mappings[0].provider").value("FLIXBUS"))
                // The provider's vocabulary and RoadScanner's, side by side — an operator
                // reconciling a mapping cannot check a table of bare UUIDs by eye.
                .andExpect(jsonPath("$.mappings[0].locationId").value(location.id().toString()))
                .andExpect(jsonPath("$.mappings[0].locationDisplayName").value("Hyderabad"))
                .andExpect(jsonPath("$.mappings[0].locationCity").value("Hyderabad"))
                .andExpect(jsonPath("$.mappings[0].providerCityId").value("58291"))
                .andExpect(jsonPath("$.mappings[0].providerStationName").value("MGBS"))
                .andExpect(jsonPath("$.mappings[0].verified").value(true))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listPassesEveryFilterThrough() throws Exception {
        when(searchProviderMappings.search(any()))
                .thenReturn(new SearchProviderMappings.Result(List.of(), 0, 1, 5, 0));

        mockMvc.perform(get("/api/v1/provider-mappings")
                        .param("provider", "FLIXBUS").param("verified", "false").param("q", "mgbs")
                        .param("page", "1").param("size", "5")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(searchProviderMappings).search(argThat(query ->
                query.provider().equals(FLIXBUS)
                        && Boolean.FALSE.equals(query.verified())
                        && "mgbs".equals(query.searchTerm())
                        && query.page() == 1
                        && query.size() == 5));
    }

    @Test
    void listDefaultsPagingAndTreatsAbsentFiltersAsNoFilter() throws Exception {
        when(searchProviderMappings.search(any()))
                .thenReturn(new SearchProviderMappings.Result(List.of(), 0, 0, 20, 0));

        mockMvc.perform(get("/api/v1/provider-mappings").with(admin()))
                .andExpect(status().isOk());

        verify(searchProviderMappings).search(argThat(query ->
                query.provider() == null && query.verified() == null && query.searchTerm() == null
                        && query.page() == 0 && query.size() == 20));
    }

    @Test
    void listReturnsAnEmptyPageRatherThanNotFound() throws Exception {
        when(searchProviderMappings.search(any()))
                .thenReturn(new SearchProviderMappings.Result(List.of(), 0, 0, 20, 0));

        mockMvc.perform(get("/api/v1/provider-mappings").param("q", "zzz").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappings").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listRejectsOutOfRangePaging() throws Exception {
        mockMvc.perform(get("/api/v1/provider-mappings").param("page", "-1").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/provider-mappings").param("size", "0").with(admin()))
                .andExpect(status().isBadRequest());
        // An unbounded page size is a denial-of-service shaped request, not a convenience.
        mockMvc.perform(get("/api/v1/provider-mappings").param("size", "500").with(admin()))
                .andExpect(status().isBadRequest());

        verify(searchProviderMappings, never()).search(any());
    }

    // ---------- GET /api/v1/provider-mappings/unmapped-locations ----------

    @Test
    void unmappedLocationsReturnsTheWorklistAsSummaries() throws Exception {
        Location location = hyderabad();
        when(searchProviderMappings.findUnmappedLocations(any()))
                .thenReturn(new SearchProviderMappings.UnmappedResult(List.of(location)));

        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations")
                        .param("provider", "FLIXBUS").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations[0].id").value(location.id().toString()))
                .andExpect(jsonPath("$.locations[0].displayName").value("Hyderabad"))
                .andExpect(jsonPath("$.locations[0].city").value("Hyderabad"));
    }

    @Test
    void unmappedLocationsDefaultsItsLimitAndForwardsTheTerm() throws Exception {
        when(searchProviderMappings.findUnmappedLocations(any()))
                .thenReturn(new SearchProviderMappings.UnmappedResult(List.of()));

        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations")
                        .param("provider", "FLIXBUS").param("q", "hyd").with(admin()))
                .andExpect(status().isOk());

        verify(searchProviderMappings).findUnmappedLocations(argThat(query ->
                query.provider().equals(FLIXBUS) && "hyd".equals(query.searchTerm()) && query.limit() == 50));
    }

    @Test
    void unmappedLocationsRequiresAProvider() throws Exception {
        // The worklist is meaningless without one — "unmapped for whom?" has no default answer.
        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations").param("provider", "  ")
                        .with(admin()))
                .andExpect(status().isBadRequest());

        verify(searchProviderMappings, never()).findUnmappedLocations(any());
    }

    @Test
    void unmappedLocationsRejectsAnOutOfRangeLimit() throws Exception {
        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations")
                        .param("provider", "FLIXBUS").param("limit", "0").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations")
                        .param("provider", "FLIXBUS").param("limit", "5000").with(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/v1/provider-mappings/{id} ----------

    @Test
    void getReturnsTheMapping() throws Exception {
        Location location = hyderabad();
        ProviderLocationMapping mapping = mappingFor(location);
        when(manageProviderMappings.getById(any())).thenReturn(Optional.of(mapping));
        stubGetLocation(location);

        mockMvc.perform(get("/api/v1/provider-mappings/{id}", mapping.id().value()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapping.id().toString()))
                .andExpect(jsonPath("$.providerStationId").value("station-1"))
                .andExpect(jsonPath("$.providerMetadata").value("{\"platform\":\"7\"}"))
                .andExpect(jsonPath("$.locationDisplayName").value("Hyderabad"));
    }

    @Test
    void getReturnsNotFoundForAnUnknownId() throws Exception {
        when(manageProviderMappings.getById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/provider-mappings/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Provider mapping not found"));
    }

    @Test
    void getReturnsBadRequestForAMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/provider-mappings/{id}", "not-a-uuid").with(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /api/v1/provider-mappings ----------

    @Test
    void createReturnsCreatedWithALocationHeader() throws Exception {
        Location location = hyderabad();
        ProviderLocationMapping created = mappingFor(location);
        when(manageProviderMappings.create(any())).thenReturn(created);
        stubGetLocation(location);

        mockMvc.perform(post("/api/v1/provider-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(location.id())))
                        .with(admin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/provider-mappings/" + created.id()))
                .andExpect(jsonPath("$.id").value(created.id().toString()));
    }

    @Test
    void createMapsTheRequestOntoDomainValueObjects() throws Exception {
        Location location = hyderabad();
        when(manageProviderMappings.create(any())).thenReturn(mappingFor(location));
        stubGetLocation(location);

        Map<String, Object> request = validRequest(location.id());
        request.put("providerStationId", "station-1");
        request.put("providerMetadata", "{\"platform\":\"7\"}");
        request.put("verified", true);

        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)).with(admin()))
                .andExpect(status().isCreated());

        verify(manageProviderMappings).create(argThat(command ->
                command.locationId().equals(location.id())
                        && command.provider().equals(FLIXBUS)
                        && "58291".equals(command.placeRef().cityId())
                        && "station-1".equals(command.placeRef().stationId())
                        && "MGBS".equals(command.placeRef().stationName())
                        && "{\"platform\":\"7\"}".equals(command.metadataJson())
                        && command.verified()));
    }

    @Test
    void createRequiresACanonicalLocation() throws Exception {
        // The catalogue is authored through /api/v1/locations. A mapping translates a place that
        // already exists; it never mints one.
        Map<String, Object> request = validRequest(LocationId.generate());
        request.remove("locationId");

        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)).with(admin()))
                .andExpect(status().isBadRequest());

        verify(manageProviderMappings, never()).create(any());
    }

    @Test
    void createRequiresAProvider() throws Exception {
        Map<String, Object> request = validRequest(LocationId.generate());
        request.remove("provider");

        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)).with(admin()))
                .andExpect(status().isBadRequest());

        verify(manageProviderMappings, never()).create(any());
    }

    @Test
    void createRequiresTheVerifiedFlag() throws Exception {
        Map<String, Object> request = validRequest(LocationId.generate());
        request.remove("verified");

        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("verified"));
    }

    @Test
    void createRejectsAMappingWithNeitherACityNorAStationId() throws Exception {
        Map<String, Object> request = validRequest(LocationId.generate());
        request.remove("providerCityId");

        // A station name alone cannot be looked up at the provider — the rule lives in
        // ProviderPlaceRef, and the handler turns it into a 400 rather than a 500.
        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)).with(admin()))
                .andExpect(status().isBadRequest());

        verify(manageProviderMappings, never()).create(any());
    }

    @Test
    void createRejectsAnOverlongProviderIdentifier() throws Exception {
        Map<String, Object> request = validRequest(LocationId.generate());
        request.put("providerCityId", "x".repeat(256));

        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("providerCityId"));
    }

    @Test
    void createReturnsNotFoundWhenTheCanonicalLocationDoesNotExist() throws Exception {
        LocationId unknown = LocationId.generate();
        when(manageProviderMappings.create(any())).thenThrow(new LocationNotFoundException(unknown));

        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(unknown))).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturnsConflictNamingTheFieldThatCollided() throws Exception {
        when(manageProviderMappings.create(any())).thenThrow(new DuplicateProviderMappingException(
                DuplicateProviderMappingException.Conflict.PROVIDER_CITY_ID_IN_USE, FLIXBUS, "58291"));

        // The field travels with the 409 so the console can attach the message to the input that
        // caused it — the three uniqueness rules fail for genuinely different reasons.
        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(LocationId.generate()))).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("providerCityId"));
    }

    // ---------- PUT /api/v1/provider-mappings/{id} ----------

    @Test
    void updateReturnsTheReplacedMapping() throws Exception {
        Location location = hyderabad();
        ProviderLocationMapping updated = mappingFor(location);
        when(manageProviderMappings.update(any())).thenReturn(updated);
        stubGetLocation(location);

        mockMvc.perform(put("/api/v1/provider-mappings/{id}", updated.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(location.id())))
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(updated.id().toString()));
    }

    @Test
    void updateTargetsThePathIdAndIgnoresTheImmutableFields() throws Exception {
        Location location = hyderabad();
        ProviderLocationMappingId pathId = ProviderLocationMappingId.generate();
        when(manageProviderMappings.update(any())).thenReturn(mappingFor(location));
        stubGetLocation(location);

        // A body naming a different location and provider must not re-point the mapping: those two
        // identify which translation this is, so changing either would silently turn it into a
        // different mapping while carrying its verified flag across.
        Map<String, Object> request = validRequest(LocationId.generate());
        request.put("provider", "ACMEBUS");

        mockMvc.perform(put("/api/v1/provider-mappings/{id}", pathId.value())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)).with(admin()))
                .andExpect(status().isOk());

        verify(manageProviderMappings).update(argThat(command -> command.id().equals(pathId)));
    }

    @Test
    void updateClearsOmittedOptionalFields() throws Exception {
        Location location = hyderabad();
        when(manageProviderMappings.update(any())).thenReturn(mappingFor(location));
        stubGetLocation(location);

        Map<String, Object> request = new HashMap<>();
        request.put("providerCityId", "58291");
        request.put("verified", false);

        // PUT is a full replace: an omitted field means "clear it", not "leave it alone".
        mockMvc.perform(put("/api/v1/provider-mappings/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)).with(admin()))
                .andExpect(status().isOk());

        verify(manageProviderMappings).update(argThat(command ->
                command.placeRef().stationId() == null && command.placeRef().stationName() == null
                        && command.metadataJson() == null && !command.verified()));
    }

    @Test
    void updateReturnsNotFoundForAnUnknownId() throws Exception {
        ProviderLocationMappingId unknown = ProviderLocationMappingId.generate();
        when(manageProviderMappings.update(any()))
                .thenThrow(new com.roadscanner.searchservice.location.domain.exception
                        .ProviderMappingNotFoundException(unknown));

        mockMvc.perform(put("/api/v1/provider-mappings/{id}", unknown.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(LocationId.generate())))
                        .with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAppliesTheSameValidationAsCreate() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("providerCityId", "58291");

        mockMvc.perform(put("/api/v1/provider-mappings/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("verified"));

        verify(manageProviderMappings, never()).update(any());
    }

    // ---------- DELETE /api/v1/provider-mappings/{id} ----------

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/provider-mappings/{id}", id).with(admin()))
                .andExpect(status().isNoContent());

        verify(manageProviderMappings).delete(argThat(mappingId -> mappingId.value().equals(id)));
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        // Nothing refers to a mapping, so a wrong translation is removed outright rather than
        // soft-deleted — and a retried DELETE must still read as success.
        mockMvc.perform(delete("/api/v1/provider-mappings/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/provider-mappings/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNoContent());
    }

    // ---------- Authorization ----------

    @Test
    void everyRouteIncludingTheReadsRequiresTheAdminRole() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/provider-mappings").with(traveler()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations").param("provider", "FLIXBUS")
                        .with(traveler()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/provider-mappings/{id}", id).with(traveler()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(LocationId.generate()))).with(traveler()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/provider-mappings/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(LocationId.generate()))).with(traveler()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/provider-mappings/{id}", id).with(traveler()))
                .andExpect(status().isForbidden());

        verifyNoUseCaseWasReached();
    }

    @Test
    void everyRouteIncludingTheReadsRejectsAnAnonymousCaller() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/provider-mappings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/provider-mappings/unmapped-locations").param("provider", "FLIXBUS"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/provider-mappings/{id}", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/provider-mappings").contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(LocationId.generate()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/provider-mappings/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(LocationId.generate()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/provider-mappings/{id}", id))
                .andExpect(status().isUnauthorized());

        verifyNoUseCaseWasReached();
    }

    /** Authorization is decided before the request reaches a use case — a rejected caller must not
     * be able to cause a write, or even a lookup that could be timed. */
    private void verifyNoUseCaseWasReached() {
        verify(searchProviderMappings, never()).search(any());
        verify(searchProviderMappings, never()).findUnmappedLocations(any());
        verify(manageProviderMappings, never()).getById(any());
        verify(manageProviderMappings, never()).create(any());
        verify(manageProviderMappings, never()).update(any());
        verify(manageProviderMappings, never()).delete(any());
    }
}
